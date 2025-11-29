package com.jgshmem.memory;

import java.nio.ByteBuffer;

public interface Memory {
    public long getSize();
    public long getPointer();
    public void release(boolean deleteFile);

    public long getLong(long address);
    public void putLong(long address, long value);
    public int getInt(long address);
    public void putInt(long address, int value);
    public short getShort(long address);
    public void putShort(long address, short value);
    public byte getByte(long address);
    public void putByte(long address, byte value);

    public long getLongVolatile(long address);
    public void putLongVolatile(long address, long value);
    public int getIntVolatile(long address);
    public void putIntVolatile(long address, int value);
    public short getShortVolatile(long address);
    public void putShortVolatile(long address, short value);
    public byte getByteVolatile(long address);
    public void putByteVolatile(long address, byte value);

    public void putByteBuffer(long address, ByteBuffer src, int length);
    public void getByteBuffer(long address, ByteBuffer dst, int length);
    public void putByteArray(long address, byte[] src, int length);
    public void getByteArray(long address, byte[] dst, int length);
}