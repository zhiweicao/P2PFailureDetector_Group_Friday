package io.friday.transport;


import io.friday.common.Monitor;
import io.friday.transport.entity.Address;
import io.friday.transport.entity.LifeCycle;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;

import java.util.List;


public interface TransportNode extends LifeCycle, Monitor {
    void send(Address address, Object object);
    void broadcast(Object object);
    void broadcastExcept(Address address, Object object);
    Channel connect(Address address);
    Channel connect(Address address, ChannelFutureListener[] channelFutureListener);
    void addConnection(Address address, Channel channel);
    void disconnect(Address address);
    void addHandlerLast(ChannelHandler[] channelHandlers);
    List<Address> getAllConnection();

}
