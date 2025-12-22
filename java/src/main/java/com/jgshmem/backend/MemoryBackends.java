package com.jgshmem.backend;

import com.jgshmem.config.Config;

public final class MemoryBackends {
	
	private MemoryBackends() {}
	
	public static MemoryBackend fromRing(Config.Ring ring) {
		if (ring.backend == null || ring.backend.isEmpty() || ring.backend.equals("mmap")) {
			return new MmapFileBackend();
		}
		if (ring.backend.equals("posix_shm")) {
			return new PosixShmBackend();
		}
		throw new IllegalArgumentException("Unknown backend: " + ring.backend);
	}
}
