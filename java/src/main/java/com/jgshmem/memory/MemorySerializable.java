package com.jgshmem.memory;

public interface MemorySerializable {
    public int writeTo(long address, Memory memory);
    public int readFrom(long address, Memory memory);
}