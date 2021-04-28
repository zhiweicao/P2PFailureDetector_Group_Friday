package io.friday.transport.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;


@Data
@AllArgsConstructor
@EqualsAndHashCode
public class Address implements Serializable {
    private String host;
    private int port;

    public Address(String str) {
        String[] data = str.split(":");
        this.host = data[0];
        this.port = Integer.parseInt(data[1]);
    }
}
