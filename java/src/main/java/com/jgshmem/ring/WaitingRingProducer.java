package com.jgshmem.ring;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import com.jgshmem.backend.MemoryBackend;
import com.jgshmem.backend.MemoryBackends;
import com.jgshmem.config.Config;
import com.jgshmem.memory.Memory;
import com.jgshmem.memory.MemorySerializable;
import com.jgshmem.memory.SharedMemory;
import com.jgshmem.utils.Builder;
import com.jgshmem.utils.MathUtils;
import com.jgshmem.utils.MemoryVolatileLong;

public class WaitingRingProducer<T extends MemorySerializable> implements RingProducer<T> {
    static final int DEFAULT_CAPACITY = 1024;
    static final int SEQ_PREFIX_PADDING = 24;
    static final int CPU_CACHE_LINE = 64;
    static final int HEADER_SIZE = CPU_CACHE_LINE + CPU_CACHE_LINE;

    private final int capacity;
    private final int capacityMinusOne;
    private final Memory memory;
    private final long headerAddress;
    private final long dataAddress;
    private long lastOfferedSeq;
    private long maxSeqBeforeWrapping;
    private final MemoryVolatileLong offerSequence;
    private final MemoryVolatileLong fetchSequence;
    private final Builder<T> builder;
    private final int maxObjectSize;
    private final boolean isPowerOfTwo;

    private final ArrayDeque<T> pool;
	private final List<T> pending;

    public WaitingRingProducer(final int capacity, final int maxObjectSize, final Builder<T> builder, final String fileName) {
        this.isPowerOfTwo = MathUtils.isPowerOfTwo(capacity);
        this.capacity = capacity;
        this.capacityMinusOne = capacity - 1;
        this.maxObjectSize = maxObjectSize;
        long totalMemorySize = calcTotalMemorySize(capacity, maxObjectSize);
        this.memory = new SharedMemory(totalMemorySize, fileName);
        this.headerAddress = memory.getPointer();
        this.dataAddress = headerAddress + HEADER_SIZE;
        this.builder = builder;
        this.offerSequence = new MemoryVolatileLong(headerAddress + SEQ_PREFIX_PADDING, memory);
        this.fetchSequence = new MemoryVolatileLong(headerAddress + SEQ_PREFIX_PADDING + CPU_CACHE_LINE, memory);
        this.lastOfferedSeq = offerSequence.get();
        this.maxSeqBeforeWrapping = calcMaxSeqBeforeWrapping();

        this.pool = new ArrayDeque<T>(256);
		for (int i = 0; i < 256; i++) {
			this.pool.add(builder.newInstance());
		}
		this.pending = new ArrayList<T>(256);
    }

    public WaitingRingProducer(int maxObjectSize, Builder<T> builder, String fileName) {
        this(DEFAULT_CAPACITY, maxObjectSize, builder, fileName);
    }

    public WaitingRingProducer(int capacity, int maxObjectSize, Class<T> cls, String fileName) {
        this(capacity, maxObjectSize, Builder.createBuilder(cls), fileName);
    }

    public WaitingRingProducer(int maxObjectSize, Class<T> cls, String fileName) {
        this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(cls), fileName);
    }

    public WaitingRingProducer(final int capacity, final int maxObjectSize, final Builder<T> builder, final Config.Ring ring) {
        this.isPowerOfTwo = MathUtils.isPowerOfTwo(capacity);
        this.capacity = capacity;
        this.capacityMinusOne = capacity - 1;
        this.maxObjectSize = maxObjectSize;
        long totalMemorySize = calcTotalMemorySize(capacity, maxObjectSize);

        MemoryBackend backend = MemoryBackends.fromRing(ring);
        this.memory = backend.open(ring, totalMemorySize, true);

        this.headerAddress = memory.getPointer();
        this.dataAddress = headerAddress + HEADER_SIZE;
        this.builder = builder;
        this.offerSequence = new MemoryVolatileLong(headerAddress + SEQ_PREFIX_PADDING, memory);
        this.fetchSequence = new MemoryVolatileLong(headerAddress + SEQ_PREFIX_PADDING + CPU_CACHE_LINE, memory);
        this.lastOfferedSeq = offerSequence.get();
        this.maxSeqBeforeWrapping = calcMaxSeqBeforeWrapping();

        this.pool = new ArrayDeque<T>(256);
        for (int i = 0; i < 256; i++) {
            this.pool.add(builder.newInstance());
        }
        this.pending = new ArrayList<T>(256);
    }
    
    @Override
    public final long getLastOfferedSequence() {
        return lastOfferedSeq;
    }

    @Override
    public final void setLastOfferedSequence(long seq) {
        this.lastOfferedSeq = seq;
    }

    @Override
    public final Memory getMemory() {
        return memory;
    }

    @Override
    public final int getCapacity() {
        return capacity;
    }

    private final long calcTotalMemorySize(int capacity, int maxObjectSize) {
        return HEADER_SIZE + ((long) capacity) * maxObjectSize;
    }

    @Override
    public final Builder<T> getBuilder() {
        return builder;
    }

    private final long calcMaxSeqBeforeWrapping() {
        return fetchSequence.get() + capacity;
    }
    
    private T poolGet() {
		T e = pool.pollFirst();
		return e != null ? e : builder.newInstance();
	}

	private void poolRelease(T e) {
		pool.addFirst(e);
	}

    @Override
    public final T nextToDispatch() {
        if (++lastOfferedSeq > maxSeqBeforeWrapping) {
            this.maxSeqBeforeWrapping = calcMaxSeqBeforeWrapping();
            if (lastOfferedSeq > maxSeqBeforeWrapping) {
                lastOfferedSeq--;
                return null;
            }
        }

        T obj = poolGet();
        pending.add(obj);
        return obj;
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
    public final void flush() {
        long seq = lastOfferedSeq - pending.size() + 1;

        for (int i = 0; i < pending.size(); i++) {
			int index = calcIndex(seq);
			long offset = calcDataOffset(index);
			T obj = pending.get(i);
			obj.writeTo(offset, memory);
			poolRelease(obj);
			seq++;
		}
		
		pending.clear();
		
		offerSequence.set(lastOfferedSeq);
    }

    @Override
    public final void close(boolean deleteFile) {
        memory.release(deleteFile);
    }
}