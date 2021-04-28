package io.friday.transport.handler;


import io.friday.transport.TransportNode;
import io.friday.transport.entity.Duplicate;
import io.friday.transport.util.SimpleSerializationConverter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.util.List;


public class MessageCodec extends ByteToMessageCodec<Object> implements Duplicate {

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        byte[] data = SimpleSerializationConverter.objectToByte(msg);
        out.writeBytes(data);
        ctx.flush();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byte[] bytes = new byte[in.readableBytes()];
        in.readBytes(bytes);
        Object obj = SimpleSerializationConverter.byteToObject(bytes);
        out.add(obj);
    }

    @Override
    public MessageCodec getNewInstance() {
        return new MessageCodec();
    }
}
