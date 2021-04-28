package io.friday.transport.server;

import io.friday.transport.entity.LifeCycle;
import io.friday.transport.handler.MessageCodec;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class NioServer implements LifeCycle {
    protected DefaultEventLoopGroup defaultEventLoopGroup;
    protected NioEventLoopGroup bossGroup;
    protected NioEventLoopGroup workerGroup;
    protected ServerBootstrap bootstrap;
    protected ChannelHandler[] channelHandlers;
    private int port;

    public NioServer(int port) {
        this.port = port;

        defaultEventLoopGroup = new DefaultEventLoopGroup();
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        bootstrap = new ServerBootstrap();
        //设置两个线程组boosGroup和workerGroup
    }

    public void setChannelHandlers(ChannelHandler[] channelHandlers) {
        this.channelHandlers = channelHandlers;
    }


    @Override
    public void init() {
        bootstrap.group(bossGroup, workerGroup)
                //设置服务端通道实现类型
                .channel(NioServerSocketChannel.class)
                //设置线程队列得到连接个数
                .option(ChannelOption.SO_BACKLOG, 128)
                //设置保持活动连接状态
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                //使用匿名内部类的形式初始化通道对象
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        //给pipeline管道设置处理器
                        ch.pipeline().addLast(
                                new MessageCodec()
                        );
                        ch.pipeline().addLast(channelHandlers);
                    }
                });//给workerGroup的EventLoop对应的管道设置处理器
        System.out.println("服务器配置完成...");
    }

    public void start() {
        try {
            ChannelFuture channelFuture = bootstrap.bind(port).sync();
            channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (channelFuture.isSuccess()) {
                        System.out.println(String.format("成功绑定%s端口，服务器开始接受请求。", port));
                    }
                }
            });
//            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {

    }

    public void close() {
        defaultEventLoopGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
