package io.friday.transport.client;

import io.friday.transport.entity.Address;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

public class SwimClient {
    protected final Bootstrap bootstrap;
    protected final NioEventLoopGroup eventExecutors;
    protected ChannelFuture channelFuture;

    protected final String destHost;
    protected final int destPort;

    public SwimClient(String destHost, int destPort, ChannelHandler[] channelHandlers) {
        this.destHost = destHost;
        this.destPort = destPort;

        bootstrap = new Bootstrap();
        eventExecutors = new NioEventLoopGroup();
        bootstrap.group(eventExecutors)
                //设置客户端的通道实现类型
                .channel(NioSocketChannel.class)
                //使用匿名内部类初始化通道
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {

                        //添加客户端通道的处理器
                        ch.pipeline().addLast(
                                new ObjectEncoder(),
                                new ObjectDecoder(ClassResolvers.cacheDisabled(this.getClass().getClassLoader()))
                        );
                        ch.pipeline().addLast(channelHandlers);
                    }
                });
    }

    public SwimClient(Address address, ChannelHandler[] channelHandlers) {
        this(address.getHost(), address.getPort(), channelHandlers);
    }


    public void send(Object msg) {
        channelFuture.channel().writeAndFlush(msg);
    }


    public void addListenerOnChannel(ChannelFutureListener channelFutureListener) {
        channelFuture.addListener(channelFutureListener);
    }
    public void close() {
        eventExecutors.shutdownGracefully();
    }

    public ChannelFuture connect() {
        return this.bootstrap.connect(destHost, destPort);

    }
}
