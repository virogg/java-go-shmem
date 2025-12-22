package com.jgshmem.utils;

import com.jgshmem.memory.Memory;

public class MemoryVolatileLong {
    private final long address;
    private final Memory memory;

    public MemoryVolatileLong(long address, Memory memory, Long value) {
        this.address = address;
        this.memory = memory;
        if (value != null) {
            set(value.longValue());
        }
    }

    public MemoryVolatileLong(long address, Memory memory) {
        this(address, memory, null);
    }

    public long get() {
        return memory.getLongVolatile(address);
    }

    public void set(long value) {
        memory.putLongVolatile(address, value);
    }
}