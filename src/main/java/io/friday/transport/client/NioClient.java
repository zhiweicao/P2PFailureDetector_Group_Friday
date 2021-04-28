package io.friday.transport.client;

import io.friday.transport.entity.Address;
import io.friday.transport.handler.MessageCodec;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.commons.lang3.ArrayUtils;

public class NioClient {
    private final Bootstrap bootstrap;
    private final NioEventLoopGroup eventExecutors;
    private ChannelFuture channelFuture;

    private final String destHost;
    private final int destPort;

    public NioClient(String destHost, int destPort, ChannelHandler[] channelHandlers) {
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
                                new MessageCodec()
                        );
                        ch.pipeline().addLast(channelHandlers);
                    }
                });
    }

    public NioClient(Address address, ChannelHandler[] channelHandlers) {
        this(address.getHost(), address.getPort(), channelHandlers);
    }


    public void send(Object msg) {
        channelFuture.channel().writeAndFlush(msg);
    }

    public Channel connect() {
        try {
            channelFuture = bootstrap.connect(destHost, destPort).sync();
            return channelFuture.channel();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void addListenerOnChannel(ChannelFutureListener channelFutureListener) {
        channelFuture.addListener(channelFutureListener);
    }
    public void close() {
        eventExecutors.shutdownGracefully();
    }
}
