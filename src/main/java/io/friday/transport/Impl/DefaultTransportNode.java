package io.friday.transport.Impl;

import io.friday.transport.TransportNode;
import io.friday.transport.client.NioClient;
import io.friday.transport.entity.Address;
import io.friday.transport.entity.TransportMessage;
import io.friday.transport.handler.TransportMessageHandler;
import io.friday.transport.server.NioServer;
import io.netty.channel.*;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultTransportNode implements TransportNode {
    private String host;
    private int port;
    private NioServer server;
    private ArrayList<NioClient> peerClients;
    private final ConcurrentHashMap<Address, Channel> destChannel;
    private ChannelHandler[] channelHandlers;

    public DefaultTransportNode(String host, int port) {
        this.host = host;
        this.port = port;
        this.channelHandlers = new ChannelHandler[0];
        server = new NioServer(port);
        peerClients = new ArrayList<>();
        destChannel = new ConcurrentHashMap<>();

        ChannelHandler[] transportChannelHandlers = new ChannelHandler[] {
                new TransportMessageHandler(this)
        };

        this.addHandlerLast(transportChannelHandlers);
    }

    @Override
    public void init() {
        server.init();
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public void stop() {
        server.close();
        destChannel.values().forEach(e -> e.close());
        peerClients.forEach(NioClient::close);
    }

    @Override
    public void send(Address address, Object object) {
        Channel channel = destChannel.get(address);
        channel.writeAndFlush(object);
    }

    @Override
    public void send(Address address, Object object, ChannelFutureListener[] channelFutureListeners) {
        Channel channel = destChannel.get(address);
        ChannelFuture channelFuture = channel.writeAndFlush(object);
        Arrays.stream(channelFutureListeners).forEach(channelFuture::addListener);
    }


    @Override
    public void broadcast(Object object) {
        List<Address> allDest = getAllConnection();
        allDest.forEach(e -> send(e, object));
    }

    @Override
    public void broadcastExcept(Address address, Object object) {
        List<Address> allDest = getAllConnection();
        allDest.remove(address);
        allDest.forEach(e -> send(e, object));
    }

    @Override
    public Channel connect(Address address) {
        return this.connect(address, new ChannelFutureListener[0]);
    }

    @Override
    public Channel connect(Address address, ChannelFutureListener[] channelFutureListener) {
        NioClient nioClient = new NioClient(address, channelHandlers);
        peerClients.add(nioClient);
        Channel channel = nioClient.connect();
        ChannelFuture channelFuture = channel.writeAndFlush(new TransportMessage(getOwnAddress(), TransportMessage.TransportType.connect));
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                destChannel.put(address, future.channel());
            }
        });

//        try {
//            channelFuture.sync();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        Arrays.stream(channelFutureListener).forEach(nioClient::addListenerOnChannel);
        return channel;
    }

    @Override
    public void addConnection(Address address, Channel channel) {
        destChannel.put(address, channel);
    }

    @Override
    public void disconnect(Address address) {
        Channel channel = destChannel.get(address);
        channel.close();
        destChannel.remove(address);
    }

    @Override
    public void addHandlerLast(ChannelHandler[] channelHandlers) {
        this.channelHandlers = ArrayUtils.addAll(this.channelHandlers ,channelHandlers);
        server.setChannelHandlers(this.channelHandlers);
    }

    @Override
    public List<Address> getAllConnection() {
        return new ArrayList<>(destChannel.keySet());
    }

    @Override
    public void list() {
        destChannel.keySet().forEach(System.out::println);
    }

    public Address getOwnAddress() {
        return new Address(this.host, this.port);
    }
}
