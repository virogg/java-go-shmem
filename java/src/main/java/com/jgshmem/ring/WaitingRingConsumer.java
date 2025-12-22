package com.jgshmem.ring;

import java.io.File;

import com.jgshmem.backend.MemoryBackend;
import com.jgshmem.backend.MemoryBackends;
import com.jgshmem.config.Config;
import com.jgshmem.memory.Memory;
import com.jgshmem.memory.MemorySerializable;
import com.jgshmem.memory.SharedMemory;
import com.jgshmem.utils.Builder;
import com.jgshmem.utils.MathUtils;
import com.jgshmem.utils.MemoryVolatileLong;

public class WaitingRingConsumer<T extends MemorySerializable> implements RingConsumer<T> {
    private static final int DEFAULT_CAPACITY = WaitingRingProducer.DEFAULT_CAPACITY;
    private static final int SEQ_PREFIX_PADDING = WaitingRingProducer.SEQ_PREFIX_PADDING;
    private static final int CPU_CACHE_LINE = WaitingRingProducer.CPU_CACHE_LINE;
    private static final int HEADER_SIZE = WaitingRingProducer.HEADER_SIZE;

    private final int capacity;
    private final int capacityMinusOne;
    private final T data;
    private long lastFetchedSeq;
    private long fetchCount;
    private final MemoryVolatileLong offerSequence;
    private final MemoryVolatileLong fetchSequence;
    private final int maxObjectSize;
    private final Memory memory;
    private final long headerAddress;
    private final long dataAddress;
    private final boolean isPowerOfTwo;

    private final Builder<T> builder;

    public WaitingRingConsumer(final int capacity, final int maxObjectSize, final Builder<T> builder, final String fileName) {
        this.capacity = (capacity == -1 ? findCapacityFromFile(fileName, maxObjectSize) : capacity);
        this.isPowerOfTwo = MathUtils.isPowerOfTwo(this.capacity);
        this.capacityMinusOne = this.capacity - 1;
        this.maxObjectSize = maxObjectSize;
        long totalMemorySize = calcTotalMemorySize(this.capacity, this.maxObjectSize);
        this.memory = new SharedMemory(totalMemorySize, fileName);
        this.headerAddress = memory.getPointer();
        this.dataAddress = headerAddress + HEADER_SIZE;
        this.builder = builder;
        this.offerSequence = new MemoryVolatileLong(headerAddress + SEQ_PREFIX_PADDING, memory);
        this.fetchSequence = new MemoryVolatileLong(headerAddress + SEQ_PREFIX_PADDING + CPU_CACHE_LINE, memory);
        this.lastFetchedSeq = fetchSequence.get();
        this.data = builder.newInstance();
    }

    public WaitingRingConsumer(int maxObjectSize, Builder<T> builder, String fileName) {
        this(DEFAULT_CAPACITY, maxObjectSize, builder, fileName);
    }

    public WaitingRingConsumer(int capacity, int maxObjectSize, Class<T> cls, String fileName) {
        this(capacity, maxObjectSize, Builder.createBuilder(cls), fileName);
    }

    public WaitingRingConsumer(int maxObjectSize, Class<T> cls, String fileName) {
        this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(cls), fileName);
    }

    public WaitingRingConsumer(final int capacity, final int maxObjectSize, final Builder<T> builder, final Config.Ring ring) {
        this.capacity = capacity;
        this.isPowerOfTwo = MathUtils.isPowerOfTwo(this.capacity);
        this.capacityMinusOne = this.capacity - 1;
        this.maxObjectSize = maxObjectSize;
        long totalMemorySize = calcTotalMemorySize(this.capacity, this.maxObjectSize);

        MemoryBackend backend = MemoryBackends.fromRing(ring);
        this.memory = backend.open(ring, totalMemorySize, false);

        this.headerAddress = memory.getPointer();
        this.dataAddress = headerAddress + HEADER_SIZE;
        this.builder = builder;
        this.offerSequence = new MemoryVolatileLong(headerAddress + SEQ_PREFIX_PADDING, memory);
        this.fetchSequence = new MemoryVolatileLong(headerAddress + SEQ_PREFIX_PADDING + CPU_CACHE_LINE, memory);
        this.lastFetchedSeq = fetchSequence.get();
        this.data = builder.newInstance();
    }

    @Override
    public final long getLastFetchedSequence() {
        return lastFetchedSeq;
    }

    @Override
    public final void setLastFetchedSequence(long seq) {
        this.lastFetchedSeq = seq;
    }

    @Override
    public final long getLastOfferedSequence() {
        return offerSequence.get();
    }

    @Override
    public final Memory getMemory() {
        return memory;
    }

    private final long calcTotalMemorySize(int capacity, int maxObjectSize) {
        return HEADER_SIZE + ((long) capacity) * maxObjectSize;
    }

    private final int findCapacityFromFile(String fileName, int maxObjectSize) {
        File file = new File(fileName);
        if (!file.exists() || file.isDirectory()) {
            throw new RuntimeException("Cannot find file: " + fileName);
        }
        long totalMemorySize = file.length();
        return calcCapacity(totalMemorySize, maxObjectSize);
    }

    private final int calcCapacity(long totalMemorySize, int maxObjectSize) {
        return (int) ((totalMemorySize - HEADER_SIZE) / maxObjectSize);
    }

    @Override
    public final Builder<T> getBuilder() {
        return builder;
    }

    @Override
    public final int getCapacity() {
        return capacity;
    }

    @Override
    public final long availableToFetch() {
        return offerSequence.get() - lastFetchedSeq;
    }

    private final long calcDataOffset(long idx) {
        return dataAddress + idx * maxObjectSize;
    }

    private final int calcIndex(long seq) {
        if (isPowerOfTwo) {
            return (int) ((seq - 1) & capacityMinusOne);
        } else {
            return (int) ((seq - 1) % capacity);
        }
    }

    @Override
    public final T fetch(boolean remove) {
        if (remove) {
            return fetchTrue();
        } else {
            return fetchFalse();
        }
    }

    @Override
    public final T fetch() {
        return fetch(true);
    }

    private final T fetchTrue() {
        fetchCount++;
        int idx = calcIndex(++lastFetchedSeq);
        long dataOffset = calcDataOffset(idx);
        data.readFrom(dataOffset, memory);
        return data;
    }

    private final T fetchFalse() {
        int idx = calcIndex(lastFetchedSeq + 1);
        long dataOffset = calcDataOffset(idx);
        data.readFrom(dataOffset, memory);
        return data;
    }

    @Override
    public final void rollback() {
        rollback(fetchCount);
    }

    @Override
    public final void rollback(long cnt) {
        if (cnt < 0 || cnt > fetchCount) {
            throw new RuntimeException("Invalid rollback request! fetched=" + fetchCount + ", requested=" + cnt);
        }

        lastFetchedSeq -= cnt;
        fetchCount -= cnt;
    }

    @Override
    public final void doneFetching() {
        fetchSequence.set(lastFetchedSeq);
        fetchCount = 0;
    }

    @Override
    public final void close(boolean deleteFile) {
        memory.release(deleteFile);
    }
}