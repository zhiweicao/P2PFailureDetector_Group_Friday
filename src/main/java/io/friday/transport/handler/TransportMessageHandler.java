package io.friday.transport.handler;


import io.friday.transport.TransportNode;
import io.friday.transport.entity.Duplicate;
import io.friday.transport.entity.TransportMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

@ChannelHandler.Sharable
public class TransportMessageHandler extends SimpleChannelInboundHandler<Object> implements Duplicate {
    private TransportNode transportNode;

    public TransportMessageHandler(TransportNode transportNode) {
        this.transportNode = transportNode;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof TransportMessage) {
            TransportMessage transportMessage = (TransportMessage) msg;
            System.out.println("收到信息: " + transportMessage);

            switch (transportMessage.getType()) {
                case connect:
                    handleConnectEvent(ctx, transportMessage);
                    break;
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handleConnectEvent(ChannelHandlerContext ctx, TransportMessage transportMessage) {
        transportNode.addConnection(transportMessage.getAddress(), ctx.channel());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        System.out.println("[" + channel.remoteAddress() + "]: 在线");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        System.out.println("[" + channel.remoteAddress() + "]: 离线");
    }

    @Override
    public TransportMessageHandler getNewInstance() {
        return new TransportMessageHandler(this.transportNode);
    }
}
