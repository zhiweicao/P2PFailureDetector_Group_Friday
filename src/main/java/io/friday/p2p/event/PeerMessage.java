package io.friday.p2p.event;

import io.friday.transport.entity.Address;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.UUID;

@Data
@EqualsAndHashCode
public class PeerMessage implements Serializable {
    UUID uuid;
    Object message;
    PeerMessageType type;

    public PeerMessage(Object message, PeerMessageType peerMessageType) {
        this.uuid = UUID.randomUUID();
        this.message = message;
        this.type = peerMessageType;
    }

    public PeerMessage(PeerMessageType peerMessageType) {
        this.uuid = UUID.randomUUID();
        this.type = peerMessageType;
    }

    public enum PeerMessageType {
        share,
        join,
        joinResponse,
        leave,
        heartbeat,
        nodeFailure
    }
}
