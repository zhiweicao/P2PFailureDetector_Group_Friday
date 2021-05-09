package io.friday.p2p;

import io.friday.transport.entity.Address;

import java.util.List;

public interface FailureDetector {
    void addNeighbours(Address address);
    List<Address> getSuspectedNeighbours();
    List<Address> getFailedNeighbours();
    List<Address> getAliveNeighbours();
}
