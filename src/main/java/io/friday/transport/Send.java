package io.friday.transport;

import io.friday.transport.entity.Address;
import io.netty.channel.ChannelFutureListener;

import java.util.List;

public interface Send {
    boolean send(Address address, Object object) throws InterruptedException;
    void send(List<Address> address, Object object) throws InterruptedException;
    boolean send(Address address, Object object, ChannelFutureListener[] channelFutureListeners) throws InterruptedException;
    void send(List<Address> address, Object object, ChannelFutureListener[] channelFutureListeners) throws InterruptedException;
}
