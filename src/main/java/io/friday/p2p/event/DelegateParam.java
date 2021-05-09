package io.friday.p2p.event;

import io.friday.transport.entity.Address;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public class DelegateParam implements Serializable {
    Address fromAddress;
    Address target;
}
