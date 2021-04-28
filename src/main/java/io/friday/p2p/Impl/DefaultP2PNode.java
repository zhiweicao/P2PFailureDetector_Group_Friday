package io.friday.p2p.Impl;

import io.friday.common.Monitor;
import io.friday.p2p.Cluster;
import io.friday.p2p.P2PEvent;
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
import io.netty.channel.ChannelHandler;
import io.netty.util.internal.ConcurrentSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultP2PNode implements P2PNode, P2PEvent, Cluster, LifeCycle, Monitor {
    private static final int EMPTY_PROCESSED_MESSAGE_PERIOD = 600*1000;

    private final Address nodeAddress;
    private final HashSet<FileShareInfo> files;
    private final ConcurrentHashMap<Address, HashSet<FileShareInfo>> allFiles;
    private final TransportNode transportNode;
    private final ReentrantLock processedMessageLock;
    private ConcurrentSet<PeerMessage> processedMessage;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    public DefaultP2PNode(String host, int port) {
        nodeAddress = new Address(host, port);
        files = new HashSet<>();
        allFiles = new ConcurrentHashMap<>();
        processedMessageLock = new ReentrantLock();
        processedMessage = new ConcurrentSet<>();
        transportNode = new DefaultTransportNode(host, port);
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
        channel.writeAndFlush(peerMessage);
    }

    @Override
    public void leave() {
        PeerMessage peerMessage = new PeerMessage(nodeAddress, PeerMessage.PeerMessageType.join);
        transportNode.broadcast(peerMessage);
    }

    @Override
    public void init() {
        transportNode.init();
    }

    @Override
    public void start() {
        scheduledThreadPoolExecutor.scheduleAtFixedRate(new ScheduledCleaner(), 10,EMPTY_PROCESSED_MESSAGE_PERIOD, TimeUnit.SECONDS);
        transportNode.start();
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
        transportNode.disconnect(leaveAddress);
        for (FileShareInfo fileShareInfo : allFiles.get(leaveAddress)) {
            files.remove(fileShareInfo);
        }

        allFiles.remove(leaveAddress);
        gossip(peerMessage);
    }

    @Override
    public boolean hasHandled(PeerMessage peerMessage) {
        boolean isContained;
        processedMessageLock.lock();
        isContained = processedMessage.contains(peerMessage);
        processedMessageLock.unlock();
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
        processedMessageLock.lock();
        processedMessage.add(peerMessage);
        processedMessageLock.unlock();
        transportNode.broadcast(peerMessage);
    }


    @Override
    public void list() {
        System.out.println("---- transportNode list ----");
        transportNode.list();
        System.out.println("---- Node file ----");
        files.forEach(System.out::println);

    }

    class ScheduledCleaner implements Runnable {
        @Override
        public void run() {
            processedMessageLock.lock();
            processedMessage = new ConcurrentSet<>();
            processedMessageLock.unlock();
        }
    }
}
