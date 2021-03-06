package io.friday.p2p.Impl;

import io.friday.p2p.FailureDetector;
import io.friday.p2p.event.PeerMessage;
import io.friday.transport.TransportNode;
import io.friday.transport.entity.Address;
import io.friday.transport.entity.LifeCycle;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.internal.ConcurrentSet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DefaultFailureDetector implements FailureDetector, LifeCycle {
    private static final int HEARTBEAT_PERIOD = 10*1000;
    private static final int INITIAL_DELAY = 10*1000;
    private static final int MANAGER_WAKEUP_PERIOD = 2*1000;
    private static final int SUSPECTED_TIMEOUT = 20*1000;
    private static final int FAILED_TIMEOUT = 30*1000;

    private final Address nodeAddress;
    private final List<Address> neighbours;
    private final TransportNode transportNode;
    private long lastHeartBeat;
    private final ConcurrentSet<Address> suspectedNeighbours;
    private final ConcurrentSet<Address> failedNeighbours;
    private final ConcurrentHashMap<Address, Long> timestampMap;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    private final List<Address> failNodeMsgQueue;


    public DefaultFailureDetector(Address address, TransportNode transportNode, List<Address> failNodeMsgQueue) {
        this.nodeAddress = address;
        this.transportNode = transportNode;
        this.neighbours = new ArrayList<>();
        this.timestampMap = new ConcurrentHashMap<>();
        this.suspectedNeighbours = new ConcurrentSet<>();
        this.failedNeighbours = new ConcurrentSet<>();
        this.scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(2);
        this.lastHeartBeat = 0;
        this.failNodeMsgQueue = failNodeMsgQueue;
    }

    @Override
    public synchronized void addNeighbours(Address address) {
        synchronized (neighbours) {
            neighbours.add(address);
        }
        timestampMap.put(address, System.currentTimeMillis());
    }

    @Override
    public List<Address> getSuspectedNeighbours() {
        return new ArrayList<>(suspectedNeighbours);
    }

    @Override
    public List<Address> getFailedNeighbours() {
        return new ArrayList<>(failedNeighbours);
    }

    @Override
    public List<Address> getAliveNeighbours() {
        return neighbours;
    }


    @Override
    public void init() {

    }

    @Override
    public void start() {
        scheduledThreadPoolExecutor.scheduleAtFixedRate(new ScheduledHeartbeatBroadcaster(), INITIAL_DELAY, MANAGER_WAKEUP_PERIOD, TimeUnit.MILLISECONDS);
        scheduledThreadPoolExecutor.scheduleAtFixedRate(new ScheduledStatusManager(), INITIAL_DELAY+1000, MANAGER_WAKEUP_PERIOD, TimeUnit.MILLISECONDS);
        lastHeartBeat = System.currentTimeMillis();
    }

    @Override
    public void stop() {
        scheduledThreadPoolExecutor.shutdown();
    }

    class ScheduledHeartbeatBroadcaster implements Runnable {
        @Override
        public void run() {
            try {
                if ((System.currentTimeMillis() - lastHeartBeat) > HEARTBEAT_PERIOD) {
                    for (Address neighbour : neighbours) {
                        PeerMessage peerMessage = new PeerMessage(nodeAddress, PeerMessage.PeerMessageType.heartbeat);
                        ChannelFutureListener channelFutureListener = future -> {
                            if (future.isSuccess()) {
                                timestampMap.put(neighbour, System.currentTimeMillis());
                            }
                        };
                        transportNode.send(neighbour, peerMessage, new ChannelFutureListener[]{channelFutureListener});
                    }
                    lastHeartBeat = System.currentTimeMillis();
                } else {
                    for (Address neighbour : neighbours) {
                        if (!timestampMap.containsKey(neighbour)) continue;
                        long interval = System.currentTimeMillis() - timestampMap.get(neighbour);
                        if (interval > SUSPECTED_TIMEOUT) {
                            PeerMessage peerMessage = new PeerMessage(nodeAddress, PeerMessage.PeerMessageType.heartbeat);
                            ChannelFutureListener channelFutureListener = future -> {
                                if (future.isSuccess()) {
                                    timestampMap.put(neighbour, System.currentTimeMillis());
                                }
                            };
                            transportNode.send(neighbour, peerMessage, new ChannelFutureListener[]{channelFutureListener});
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println(e.getMessage());
            }

        }
    }

    class ScheduledStatusManager implements Runnable {
        @Override
        public void run() {
            for (Address neighbour : neighbours) {
                if (!timestampMap.containsKey(neighbour)) {
                    System.out.println("timestampMap does not exist neighbour:" + neighbour);
                    continue;
                }
                long interval = System.currentTimeMillis() - timestampMap.get(neighbour);

                if (!failedNeighbours.contains(neighbour)) {
                    if (!suspectedNeighbours.contains(neighbour)) {
                        if (interval > SUSPECTED_TIMEOUT) {
                            System.out.println(neighbour + "????????????????????????");
                            suspectedNeighbours.add(neighbour);
                        }
                    } else {
                        if (interval > FAILED_TIMEOUT) {
                            System.out.println(neighbour + "????????????????????????");
                            suspectedNeighbours.remove(neighbour);
                            failedNeighbours.add(neighbour);
                            synchronized (neighbours) {
                                neighbours.remove(neighbour);
                            }
                            synchronized (failNodeMsgQueue) {
                                System.out.println("?????????????????????"+ neighbour + "???????????????");
                                failNodeMsgQueue.add(neighbour);
                                failNodeMsgQueue.notifyAll();
                            }


                        }
                    }
                }
            }
        }
    }
}
