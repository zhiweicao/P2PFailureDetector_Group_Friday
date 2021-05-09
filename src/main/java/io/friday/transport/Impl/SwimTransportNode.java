package io.friday.transport.Impl;

import io.friday.transport.Send;

import io.friday.transport.client.NioClient;
import io.friday.transport.client.SwimClient;
import io.friday.transport.entity.Address;
import io.friday.transport.entity.LifeCycle;
import io.friday.transport.server.NioServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SwimTransportNode implements Send, LifeCycle {
    private String host;
    private int port;
    private NioServer server;
    private ChannelHandler[] channelHandlers;
    public SwimTransportNode(String host, int port, ChannelHandler[] channelHandlers) {
        this.host = host;
        this.port = port;
        server = new NioServer(port, channelHandlers);
        this.channelHandlers = channelHandlers;
    }
    @Override
    public boolean send(Address address, Object object) throws InterruptedException {
        return this.send(address, object, new ChannelFutureListener[0]);
    }

    @Override
    public void send(List<Address> addresses, Object object) {
        addresses.forEach(e -> {
            try {
                this.send(e, object);
            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            }
        });
    }

    @Override
    public boolean send(Address address, Object object, ChannelFutureListener[] channelFutureListeners) throws InterruptedException {
//        NioClient nioClient = new NioClient(address, channelHandlers);
//        Channel channel = nioClient.connect();
//        System.out.println("channel: " +channel.isActive());
//        ChannelFuture channelFuture = channel.writeAndFlush(object);
//        Arrays.stream(channelFutureListeners).forEach(channelFuture::addListener);
//        channelFuture.sync();
//        nioClient.close();
        SwimClient swimClient = new SwimClient(address, channelHandlers);
        ChannelFuture cf = swimClient.connect();
        try {
            cf.sync();
        } catch (Exception e) {
            return false;
        }
        ChannelFuture channelFuture = cf.channel().writeAndFlush(object);
        Arrays.stream(channelFutureListeners).forEach(channelFuture::addListener);
        channelFuture.sync();
        swimClient.close();
        return true;
    }

    @Override
    public void send(List<Address> addresses, Object object, ChannelFutureListener[] channelFutureListeners) throws InterruptedException {
        addresses.forEach(e -> {
            try {
                this.send(e, object, channelFutureListeners);
            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            }
        });
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
    }

}
