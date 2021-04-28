package io.friday.p2p;

import io.friday.transport.entity.Address;

public interface Cluster {
    void join(Address address);
    void leave();
}
