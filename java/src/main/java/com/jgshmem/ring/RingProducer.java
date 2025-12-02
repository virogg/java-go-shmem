package com.jgshmem.ring;

import com.jgshmem.memory.Memory;
import com.jgshmem.memory.MemorySerializable;
import com.jgshmem.utils.Builder;

public interface RingProducer<T extends MemorySerializable>{
    public Memory getMemory();
    public int getCapacity();
    public Builder<T> getBuilder();
    public long getLastOfferedSequence();
    public void setLastOfferedSequence(long sequence);
    public T nextToDispatch();
    public void flush();
    public void close(boolean deleteFile);
}