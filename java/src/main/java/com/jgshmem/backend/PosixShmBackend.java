package com.jgshmem.backend;

import com.jgshmem.memory.Memory;
import com.jgshmem.memory.PosixShmMemory;
import com.jgshmem.config.Config;

public class PosixShmBackend implements MemoryBackend {
	@Override
	public Memory open(Config.Ring ring, long totalSize, boolean create) {
		if (ring.shmName == null || ring.shmName.isEmpty()) {
			throw new IllegalArgumentException("posix_shm backend requires shmName");
		}
		return new PosixShmMemory(totalSize, ring.shmName, create);
	}
}