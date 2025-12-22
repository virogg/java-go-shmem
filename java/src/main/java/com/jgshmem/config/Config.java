package com.jgshmem.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
	@SerializedName("rings")
	public Rings rings;

	public transient Ring go2java;
	public transient Ring java2go;

	Config() {}

	private void initTransientFields() {
		if (rings != null) {
			this.go2java = rings.go2java;
			this.java2go = rings.java2go;
		}
	}

	public static Config load(Path path) throws IOException {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Gson gson = new GsonBuilder().create();
			Config config = gson.fromJson(reader, Config.class);

			if (config == null) {
				throw new IOException("Failed to parse config file: " + path);
			}

			config.initTransientFields();
			config.validate();
			return config;
		}
	}

	public void validate() {
		if (rings == null) {
			throw new IllegalArgumentException("Missing 'rings' section in config");
		}
		if (rings.go2java == null) {
			throw new IllegalArgumentException("Missing 'rings.go2java' in config");
		}
		if (rings.java2go == null) {
			throw new IllegalArgumentException("Missing 'rings.java2go' in config");
		}

		rings.go2java.validate("go2java");
		rings.java2go.validate("java2go");
	}

	public static class Rings {
		@SerializedName("go2java")
		public Ring go2java;

		@SerializedName("java2go")
		public Ring java2go;
	}

	public static class Ring {
		@SerializedName("filename")
		public String filename;

		@SerializedName("backend")
		public String backend = "mmap";

		@SerializedName("shmName")
		public String shmName;

		@SerializedName("capacity")
		public int capacity;

		@SerializedName("maxObjectSize")
		public int maxObjectSize;

		@SerializedName("timeoutMs")
		public int timeoutMs = 5000;

		public void validate(String ringName) {
			if (capacity <= 0) {
				throw new IllegalArgumentException(ringName + ": capacity must be > 0, got " + capacity);
			}

			if (maxObjectSize <= 0) {
				throw new IllegalArgumentException(ringName + ": maxObjectSize must be > 0, got " + maxObjectSize);
			}

			if (timeoutMs < 0) {
				throw new IllegalArgumentException(ringName + ": timeoutMs must be >= 0, got " + timeoutMs);
			}

			if (backend == null || backend.isEmpty()) {
				backend = "mmap";
			}
			backend = backend.toLowerCase();

			if ("mmap".equals(backend)) {
				if (filename == null || filename.isEmpty()) {
					throw new IllegalArgumentException(ringName + ": mmap backend requires filename");
				}
			} else if ("posix_shm".equals(backend)) {
				if (shmName == null || shmName.isEmpty()) {
					throw new IllegalArgumentException(ringName + ": posix_shm backend requires shmName");
				}
				if (!shmName.startsWith("/")) {
					throw new IllegalArgumentException(ringName + ": shmName must start with '/', got " + shmName);
				}
			} else {
				throw new IllegalArgumentException(ringName + ": unknown backend '" + backend + "' (expected 'mmap' or 'posix_shm')");
			}
		}
	}
}