package io.friday.p2p.entity;

import io.friday.transport.entity.Address;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public class FileShareInfo implements Serializable {
    FileInfo fileInfo;
    Address address;
}
