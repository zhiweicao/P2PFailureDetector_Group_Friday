package io.friday.p2p.event;

import io.friday.transport.entity.Address;
import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

@Data
public class FailureDetectorEvent implements Serializable {
    UUID uuid;
    FailureDetectorEventType failureDetectorEventType;

    public FailureDetectorEvent(FailureDetectorEventType failureDetectorEventType) {
        this.uuid = UUID.randomUUID();
        this.failureDetectorEventType = failureDetectorEventType;
    }

    public enum FailureDetectorEventType {
        heartBeat
    }
}
