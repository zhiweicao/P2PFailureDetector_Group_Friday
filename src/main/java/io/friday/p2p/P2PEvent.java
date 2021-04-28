package io.friday.p2p;

import io.friday.p2p.event.PeerMessage;
import io.netty.channel.Channel;

public interface P2PEvent {
    void handleShareMessage(PeerMessage peerMessage, Channel channel);
    void handleJoinMessage(PeerMessage peerMessage, Channel channel);
    void handleJoinResponseMessage(PeerMessage peerMessage, Channel channel);
    void handleLeaveMessage(PeerMessage peerMessage, Channel channel);
    boolean hasHandled(PeerMessage peerMessage);
}
