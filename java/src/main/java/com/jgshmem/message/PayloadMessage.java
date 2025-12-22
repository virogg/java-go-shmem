package com.jgshmem.message;

import java.nio.ByteBuffer;

import com.jgshmem.memory.Memory;
import com.jgshmem.memory.MemorySerializable;
/** 
 * A special MemorySerializable object that allows to send anything through ring buffer as a ByteBuffer.
 * This effectively makes ring buffer message agnostic.
 * 
 * It has a 4-byte integer denoting the size of the payload, followed by the payload itself.
 * 
 * Note that the max payload size must be known beforehand.
 */

public class PayloadMessage implements MemorySerializable {    
    public int payloadSize;
    public final ByteBuffer payload;

    public static final int getMaxSize(int maxPayloadSize) {
        return 4 + maxPayloadSize;
    }

    public PayloadMessage(int maxPayloadSize) {
        this.payload = ByteBuffer.allocateDirect(maxPayloadSize);
    }

    @Override
    public int writeTo(long address, Memory memory) {
        memory.putInt(address, payloadSize);
        payload.limit(payloadSize).position(0);
        memory.putByteBuffer(address + 4, payload, payloadSize);
        return 4 + payloadSize;
    }

    @Override
    public int readFrom(long address, Memory memory) {
        this.payloadSize = memory.getInt(address);
        payload.clear();
        memory.getByteBuffer(address + 4, payload, payloadSize);
        payload.flip();
        return 4 + payloadSize;
    }
}