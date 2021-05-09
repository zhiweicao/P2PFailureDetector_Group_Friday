package io.friday.p2p.Impl;

import io.friday.common.Monitor;
import io.friday.p2p.Cluster;
import io.friday.p2p.P2PEventHandler;
import io.friday.p2p.P2PNode;
import io.friday.p2p.entity.FileInfo;
import io.friday.p2p.entity.FileShareInfo;
import io.friday.p2p.event.DelegateParam;
import io.friday.p2p.event.DelegateResponseParam;
import io.friday.p2p.event.FileShareParam;
import io.friday.p2p.event.PeerMessage;
import io.friday.p2p.handler.PeerMessageHandler;
import io.friday.transport.Impl.SwimTransportNode;
import io.friday.transport.entity.Address;
import io.friday.transport.entity.LifeCycle;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.util.internal.ConcurrentSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SwimP2PNode implements P2PNode, P2PEventHandler, Cluster, LifeCycle, Monitor {
    private static final int DETECT_PERIOD = 5 * 1000;
    private static final int SUSPECTED_PERIOD = 3 * 1000;
    private static final int INITIAL_DELAY = 10 * 1000;
    private static final int WAKEUP_PERIOD = 1 * 1000;
    private final Address nodeAddress;
    private final List<Address> neighbours;
    private final List<Address> suspectedNeighbours;
    private final List<Address> refuseSendAddress;
    private final HashSet<FileShareInfo> files;
    private final ConcurrentHashMap<Address, HashSet<FileShareInfo>> allFiles;
    private final SwimTransportNode transportNode;
    private final ConcurrentSet<PeerMessage> processedMessage;
    private AtomicLong lastDetect;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    public SwimP2PNode(String host, int port) {
        this.nodeAddress = new Address(host, port);
        neighbours = new ArrayList<>();
        suspectedNeighbours = new ArrayList<>();
        refuseSendAddress = new ArrayList<>();
        files = new HashSet<>();
        allFiles = new ConcurrentHashMap<>();
        processedMessage = new ConcurrentSet<>();
        ChannelHandler[] p2pChannelHandler = new ChannelHandler[] {
                new PeerMessageHandler(this)
        };
        this.transportNode = new SwimTransportNode(host, port, p2pChannelHandler);
        lastDetect = new AtomicLong();
        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
    }

    @Override
    public void list() {
        System.out.println("---- transportNode list ----");
        neighbours.forEach(System.out::println);
        System.out.println("---- shared file ----");
        files.forEach(System.out::println);
    }

    @Override
    public void join(Address address) {
        PeerMessage peerMessage = new PeerMessage(nodeAddress, PeerMessage.PeerMessageType.join);
        ChannelFutureListener channelFutureListener = future -> {
            if (future.isSuccess()) {
                synchronized (neighbours) {
                    neighbours.add(address);
                }
            }
        };
        try {
            transportNode.send(address, peerMessage, new ChannelFutureListener[]{channelFutureListener});
        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
        }
    }

    @Override
    public void share(List<FileInfo> fileInfoList) {
        List<FileShareInfo> fileShareInfoList = buildOwnShareInfo(fileInfoList);
        files.addAll(fileShareInfoList);
        allFiles.put(nodeAddress, new HashSet<>(fileShareInfoList));

        FileShareParam fileShareParam = new FileShareParam(fileShareInfoList);
        PeerMessage peerMessage = new PeerMessage(fileShareParam, PeerMessage.PeerMessageType.share);
        gossip(peerMessage);
    }

    @Override
    public void leave() {
        PeerMessage peerMessage = new PeerMessage(nodeAddress, PeerMessage.PeerMessageType.leave);
        transportNode.send(neighbours, peerMessage);
    }

    @Override
    public void handleShareMessage(PeerMessage peerMessage, Channel channel) {
        assert (peerMessage.getMessage() instanceof FileShareParam);
        FileShareParam fileShareParam = (FileShareParam) peerMessage.getMessage();
        addSharedFiles(fileShareParam);
        gossip(peerMessage);
    }

    @Override
    public void handleJoinMessage(PeerMessage peerMessage, Channel channel) {
        assert (peerMessage.getMessage() instanceof Address);
        Address fromAddress = (Address) peerMessage.getMessage();
        List<FileShareInfo> fileShareInfoList = new ArrayList<>(files);
        FileShareParam fileShareParam = new FileShareParam(fileShareInfoList);
        PeerMessage joinResponse = new PeerMessage(fileShareParam, PeerMessage.PeerMessageType.joinResponse);
        synchronized (neighbours) {
            neighbours.add(fromAddress);
        }
        try {
            transportNode.send(fromAddress, joinResponse);
        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
        }
    }

    @Override
    public void handleJoinResponseMessage(PeerMessage peerMessage, Channel channel) {
        assert (peerMessage.getMessage() instanceof FileShareParam);
        FileShareParam fileShareParam = (FileShareParam) peerMessage.getMessage();
        addSharedFiles(fileShareParam);
    }

    @Override
    public void handleLeaveMessage(PeerMessage peerMessage, Channel channel) {
        assert (peerMessage.getMessage() instanceof Address);

        Address leaveAddress = (Address) peerMessage.getMessage();
        removeAddressFiles(leaveAddress);
        gossip(peerMessage);
    }

    @Override
    public void handleNodeFailureMessage(PeerMessage peerMessage, Channel channel) {
        assert (peerMessage.getMessage() instanceof Address);

        Address failedAddress = (Address) peerMessage.getMessage();
        removeAddressFiles(failedAddress);
        gossip(peerMessage);
    }

    @Override
    public void handleDelegateMessage(PeerMessage peerMessage, Channel channel) {
        assert (peerMessage.getMessage() instanceof DelegateParam);
        DelegateParam delegateParam = (DelegateParam) peerMessage.getMessage();
        Address target = delegateParam.getTarget();
        Address client = delegateParam.getFromAddress();

        PeerMessage ping = new PeerMessage(nodeAddress, PeerMessage.PeerMessageType.delegatedPing);
        ChannelFutureListener channelFutureListener = future -> {
            if (future.isSuccess()) {
                DelegateResponseParam delegateResponseParam = new DelegateResponseParam(target, true);
                transportNode.send(client, new PeerMessage(delegateResponseParam, PeerMessage.PeerMessageType.delegateResponse));
            }
        };
        try {
            if (!transportNode.send(target, ping, new ChannelFutureListener[]{channelFutureListener})) {
                DelegateResponseParam delegateResponseParam = new DelegateResponseParam(target, false);
                transportNode.send(client, new PeerMessage(delegateResponseParam, PeerMessage.PeerMessageType.delegateResponse));
            }
        } catch (Exception e) {
            e.printStackTrace();
            DelegateResponseParam delegateResponseParam = new DelegateResponseParam(target, false);
            try {
                transportNode.send(client, new PeerMessage(delegateResponseParam, PeerMessage.PeerMessageType.delegateResponse));
            } catch (InterruptedException interruptedException) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void handleDelegateResponseMessage(PeerMessage peerMessage, Channel channel) {
        assert (peerMessage.getMessage() instanceof DelegateResponseParam);
        DelegateResponseParam delegateResponseParam = (DelegateResponseParam) peerMessage.getMessage();

        if (delegateResponseParam.getAliveness()) {
            synchronized (suspectedNeighbours) {
                if (suspectedNeighbours.contains(delegateResponseParam.getTarget())) {
                    suspectedNeighbours.clear();
                }
            }
        }
    }

    @Override
    public boolean hasHandled(PeerMessage peerMessage) {
        boolean isContained;
        synchronized (processedMessage) {
            isContained = processedMessage.contains(peerMessage);
        }
        return isContained;
    }

    private void addSharedFiles(FileShareParam fileShareParam) {
        files.addAll(fileShareParam.getFileShareInfoList());
        for (FileShareInfo fileShareInfo : fileShareParam.getFileShareInfoList()) {
            if (!allFiles.containsKey(fileShareInfo.getAddress())) {
                allFiles.put(fileShareInfo.getAddress(), new HashSet<>());
            }
            HashSet<FileShareInfo> fileShareInfos = allFiles.get(fileShareInfo.getAddress());
            fileShareInfos.add(fileShareInfo);
        }
    }
    private List<FileShareInfo> buildOwnShareInfo(List<FileInfo> fileInfos) {
        List<FileShareInfo> res = new ArrayList<>();
        for (FileInfo fileInfo : fileInfos) {
            res.add(new FileShareInfo(fileInfo, nodeAddress));
        }
        return res;
    }

    private void gossip(PeerMessage peerMessage) {
        synchronized (processedMessage) {
            processedMessage.add(peerMessage);
        }

        transportNode.send(neighbours, peerMessage);
    }

    private void removeAddressFiles(Address address) {
        if (!allFiles.containsKey(address)) return;
        for (FileShareInfo fileShareInfo : allFiles.get(address)) {
            files.remove(fileShareInfo);
        }

        allFiles.remove(address);
    }

    private void processNodeFailure(Address address) {
        System.out.println("Handle failed Node: " + address);
        PeerMessage msg = new PeerMessage(address, PeerMessage.PeerMessageType.nodeFailure);
        removeAddressFiles(address);
        synchronized (neighbours) {
            neighbours.remove(address);
        }
        synchronized (suspectedNeighbours) {
            suspectedNeighbours.clear();
        }
        gossip(msg);
    }

    @Override
    public List<FileShareInfo> lookUp() {
        return null;
    }

    @Override
    public List<FileShareInfo> get(FileInfo fileInfo) {
        return null;
    }

    public void addRefuseAddress(Address address) {
        refuseSendAddress.add(address);
    }

    @Override
    public void init() {
        transportNode.init();
    }

    @Override
    public void start() {
        transportNode.start();
        lastDetect.set(System.currentTimeMillis());
        scheduledThreadPoolExecutor.scheduleAtFixedRate(new SwimFailureDetection(), INITIAL_DELAY, WAKEUP_PERIOD, TimeUnit.MILLISECONDS);
        scheduledThreadPoolExecutor.scheduleAtFixedRate(new FailureNodeCleaner(), INITIAL_DELAY, WAKEUP_PERIOD, TimeUnit.MILLISECONDS);

    }

    @Override
    public void stop() {
        transportNode.stop();
    }
    class SwimFailureDetection implements Runnable {
        @Override
        public void run() {
            if (System.currentTimeMillis() - lastDetect.get() > DETECT_PERIOD) {
                try {
                    if (neighbours.isEmpty()) return;
                    Address target = neighbours.get(new Random().nextInt(neighbours.size()));
                    PeerMessage peerMessage = new PeerMessage(nodeAddress, PeerMessage.PeerMessageType.heartbeat);
                    lastDetect.set(System.currentTimeMillis());

                    if (!refuseSendAddress.contains(target) && transportNode.send(target, peerMessage)) {
                        return;
                    }

                    synchronized (suspectedNeighbours) {
                        suspectedNeighbours.add(target);
                    }

                    for (Address neighbour : neighbours) {
                        if (neighbour != target) {
                            DelegateParam delegateParam = new DelegateParam(nodeAddress, target);
                            PeerMessage delegate = new PeerMessage(delegateParam, PeerMessage.PeerMessageType.delegate);
                            transportNode.send(neighbour, delegate);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class FailureNodeCleaner implements Runnable {
        @Override
        public void run() {
            if (!suspectedNeighbours.isEmpty() && System.currentTimeMillis() - lastDetect.get() > SUSPECTED_PERIOD) {
                for (Address address : suspectedNeighbours) {
                    processNodeFailure(address);
                }
                synchronized (suspectedNeighbours) {
                    suspectedNeighbours.clear();
                }
            }
        }
    }
}
