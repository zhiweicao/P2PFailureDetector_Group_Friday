package io.friday.p2p.handler;

import io.friday.p2p.P2PEventHandler;
import io.friday.p2p.event.PeerMessage;
import io.friday.transport.entity.Duplicate;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

@ChannelHandler.Sharable
public class PeerMessageHandler extends SimpleChannelInboundHandler<PeerMessage> implements Duplicate {
    private P2PEventHandler p2PEventHandler;

    public PeerMessageHandler(P2PEventHandler p2PEventHandler) {
        this.p2PEventHandler = p2PEventHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, PeerMessage msg) throws Exception {
        System.out.println("收到信息: " + msg);
        if (p2PEventHandler.hasHandled(msg)) return;
        switch (msg.getType()) {
            case join:
                handleJoinEvent(ctx, msg);
                break;
            case joinResponse:
                handleJoinResponseEvent(ctx, msg);
                break;
            case share:
                handleShareEvent(ctx, msg);
                break;
            case leave:
                handleLeaveEvent(ctx, msg);
                break;
            case nodeFailure:
                handleNodeFailureEvent(ctx, msg);
                break;
            case heartbeat:
                //无需处理
                break;
            case delegatedPing:
                //无需处理
                break;
            case delegate:
                p2PEventHandler.handleDelegateMessage(msg, ctx.channel());
                break;
            case delegateResponse:
                p2PEventHandler.handleDelegateResponseMessage(msg, ctx.channel());
                break;
        }
    }
    private void handleJoinEvent(ChannelHandlerContext ctx, PeerMessage peerMessage) {
        p2PEventHandler.handleJoinMessage(peerMessage, ctx.channel());
    }
    private void handleJoinResponseEvent(ChannelHandlerContext ctx, PeerMessage peerMessage) {
        p2PEventHandler.handleJoinResponseMessage(peerMessage, ctx.channel());
    }
    private void handleShareEvent(ChannelHandlerContext ctx, PeerMessage peerMessage) {
        p2PEventHandler.handleShareMessage(peerMessage, ctx.channel());

    }
    private void handleLeaveEvent(ChannelHandlerContext ctx, PeerMessage peerMessage) {
        p2PEventHandler.handleLeaveMessage(peerMessage, ctx.channel());
    }

    private void handleNodeFailureEvent(ChannelHandlerContext ctx, PeerMessage peerMessage) {
        p2PEventHandler.handleNodeFailureMessage(peerMessage, ctx.channel());
    }
    @Override
    public PeerMessageHandler getNewInstance() {
        return new PeerMessageHandler(this.p2PEventHandler);
    }
}
