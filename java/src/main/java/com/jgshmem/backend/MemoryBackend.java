package com.jgshmem.backend;

import com.jgshmem.memory.Memory;
import com.jgshmem.config.Config;

public interface MemoryBackend {
	Memory open(Config.Ring ring, long totalSize, boolean create);
}