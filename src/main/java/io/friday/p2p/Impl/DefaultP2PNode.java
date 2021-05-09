package io.friday.p2p.Impl;

import io.friday.common.Monitor;
import io.friday.p2p.Cluster;
import io.friday.p2p.P2PEventHandler;
import io.friday.p2p.P2PNode;
import io.friday.p2p.entity.FileInfo;
import io.friday.p2p.entity.FileShareInfo;
import io.friday.p2p.event.FileShareParam;
import io.friday.p2p.event.PeerMessage;
import io.friday.p2p.handler.PeerMessageHandler;
import io.friday.transport.Impl.DefaultTransportNode;
import io.friday.transport.TransportNode;
import io.friday.transport.entity.Address;
import io.friday.transport.entity.LifeCycle;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.util.internal.ConcurrentSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultP2PNode implements P2PNode, P2PEventHandler, Cluster, LifeCycle, Monitor {
    private static final int EMPTY_PROCESSED_MESSAGE_PERIOD = 600;
    private static final int INITIAL_DELAY = 10;

    private final Address nodeAddress;
    private final HashSet<FileShareInfo> files;
    private final ConcurrentHashMap<Address, HashSet<FileShareInfo>> allFiles;
    private final TransportNode transportNode;
    private final DefaultFailureDetector failureDetector;
    private final ConcurrentSet<PeerMessage> processedMessage;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    private final List<Address> failNodeMsgQueue;

    public DefaultP2PNode(String host, int port) {
        nodeAddress = new Address(host, port);
        files = new HashSet<>();
        allFiles = new ConcurrentHashMap<>();
        processedMessage = new ConcurrentSet<>();
        failNodeMsgQueue = new ArrayList<>();
        transportNode = new DefaultTransportNode(host, port);
        failureDetector = new DefaultFailureDetector(this.nodeAddress, transportNode, failNodeMsgQueue);
        ChannelHandler[] p2pChannelHandler = new ChannelHandler[] {
                new PeerMessageHandler(this)
        };
        transportNode.addHandlerLast(p2pChannelHandler);
        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
    }

    @Override
    public List<FileShareInfo> get(FileInfo fileInfo) {
        List<FileShareInfo> res = new ArrayList<>();
        for (FileShareInfo fileShareInfo : files) {
            if (fileShareInfo.getFileInfo().equals(fileInfo)) {
                res.add(fileShareInfo);
            }
        }
        return res;
    }

    @Override
    public synchronized void share(List<FileInfo> fileInfoList) {
        List<FileShareInfo> fileShareInfoList = buildOwnShareInfo(fileInfoList);
        files.addAll(fileShareInfoList);
        allFiles.put(nodeAddress, new HashSet<>(fileShareInfoList));

        FileShareParam fileShareParam = new FileShareParam(fileShareInfoList);
        PeerMessage peerMessage = new PeerMessage(fileShareParam, PeerMessage.PeerMessageType.share);
        gossip(peerMessage);
    }

    @Override
    public List<FileShareInfo> lookUp() {
        return new ArrayList<>(files);
    }

    @Override
    public void join(Address address) {
        Channel channel = transportNode.connect(address);
        PeerMessage peerMessage = new PeerMessage(nodeAddress, PeerMessage.PeerMessageType.join);
        ChannelFutureListener channelFutureListener = future -> {
            if (future.isSuccess()) {
                failureDetector.addNeighbours(address);
            }
        };

//        transportNode.send(address, peerMessage, new ChannelFutureListener[]{channelFutureListener});
        ChannelFuture channelFuture = channel.writeAndFlush(peerMessage);
        channelFuture.addListener(channelFutureListener);
        try {
            channelFuture.sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
//        transportNode.send(address, peerMessage, new ChannelFutureListener[]{channelFutureListener});
    }

    @Override
    public void leave() {
        PeerMessage peerMessage = new PeerMessage(nodeAddress, PeerMessage.PeerMessageType.leave);
        transportNode.broadcast(peerMessage);
    }

    @Override
    public void init() {
        transportNode.init();
    }

    @Override
    public void start() {
        scheduledThreadPoolExecutor.scheduleAtFixedRate(new ScheduledCleaner(), INITIAL_DELAY, EMPTY_PROCESSED_MESSAGE_PERIOD, TimeUnit.SECONDS);
        transportNode.start();
        failureDetector.start();
        new Thread(new FailNodeConsumer(failNodeMsgQueue)).start();
    }

    @Override
    public void stop() {
        transportNode.stop();
    }

    @Override
    public synchronized void handleShareMessage(PeerMessage peerMessage, Channel channel) {
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
        transportNode.send(fromAddress, joinResponse);
        failureDetector.addNeighbours(fromAddress);
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

    }

    @Override
    public void handleDelegateResponseMessage(PeerMessage peerMessage, Channel channel) {

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
        transportNode.broadcast(peerMessage);
    }

    private void processNodeFailure(Address address) {
        System.out.println("Handle failed Node: " + address);
        PeerMessage msg = new PeerMessage(address, PeerMessage.PeerMessageType.nodeFailure);
        transportNode.disconnect(address);
        removeAddressFiles(address);
        gossip(msg);
    }

    private void removeAddressFiles(Address address) {
        if (!allFiles.containsKey(address)) return;
        for (FileShareInfo fileShareInfo : allFiles.get(address)) {
            files.remove(fileShareInfo);
        }

        allFiles.remove(address);
    }
    @Override
    public void list() {
        System.out.println("---- transportNode list ----");
        transportNode.list();
        System.out.println("---- shared file ----");
        files.forEach(System.out::println);
        System.out.println("---- FailureDetector alive neighbours list ----");
        failureDetector.getAliveNeighbours().forEach(System.out::println);
        System.out.println("---- FailureDetector suspected list ----");
        failureDetector.getSuspectedNeighbours().forEach(System.out::println);
        System.out.println("---- FailureDetector failed neighbours list ----");
        failureDetector.getFailedNeighbours().forEach(System.out::println);

    }

    class ScheduledCleaner implements Runnable {
        @Override
        public void run() {
            synchronized (processedMessage) {
                processedMessage.clear();
            }
        }
    }

    class FailNodeConsumer implements Runnable {
        private final List<Address> eventQueue;

        public FailNodeConsumer(List<Address> eventQueue) {
            this.eventQueue = eventQueue;
        }
        @Override
        public void run() {
            try {
                while(eventQueue.isEmpty()) {
                    synchronized (eventQueue) {
                        while(eventQueue.isEmpty()) {
                            eventQueue.wait();

                        }
                        for (Address address : eventQueue) {
                            processNodeFailure(address);
                        }
                        eventQueue.clear();
                    }
                }
            } catch (InterruptedException interruptedException) {
                System.out.println("线程崩溃："+interruptedException.getMessage());
                interruptedException.printStackTrace();
            }
        }
    }
}
