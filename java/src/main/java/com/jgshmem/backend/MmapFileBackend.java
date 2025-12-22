package com.jgshmem.backend;

import com.jgshmem.memory.Memory;
import com.jgshmem.memory.SharedMemory;
import com.jgshmem.config.Config;

public class MmapFileBackend implements MemoryBackend {
	@Override
	public Memory open(Config.Ring ring, long totalSize, boolean create) {
		return new SharedMemory(totalSize, ring.filename);
	}
}