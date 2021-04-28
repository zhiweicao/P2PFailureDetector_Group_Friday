package io.friday.transport.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class TransportMessage implements Serializable {
    Address address;
    TransportType type;

    public enum TransportType {
        connect,
        connectResponse
    }
}
