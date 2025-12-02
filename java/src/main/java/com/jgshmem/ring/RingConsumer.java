package com.jgshmem.ring;

import com.jgshmem.memory.Memory;
import com.jgshmem.memory.MemorySerializable;
import com.jgshmem.utils.Builder;

public interface RingConsumer<T extends MemorySerializable>{
    public Memory getMemory();
    public int getCapacity();
    public Builder<T> getBuilder();
    public long getLastFetchedSequence();
    public void setLastFetchedSequence(long sequence);
    public long getLastOfferedSequence();
    public long availableToFetch();
    public T fetch(boolean remove);
    public T fetch();
    public void rollback();
    public void rollback(long cnt);
    public void doneFetching();
    public void close(boolean deleteFile);
}