package com.jgshmem.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigTest {
    @TempDir
    Path tempDir;

    private Path writeConfig(String content) throws IOException {
        Path path = tempDir.resolve("config.json");
        Files.writeString(path, content);
        return path;
    }

    @Test
    void testLoadValidConfig() throws IOException {
        String content = "{\n" +
            "  \"rings\": {\n" +
            "    \"go2java\": {\n" +
            "      \"filename\": \"/tmp/go2java.mmap\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 1028\n" +
            "    },\n" +
            "    \"java2go\": {\n" +
            "      \"filename\": \"/tmp/java2go.mmap\",\n" +
            "      \"capacity\": 512,\n" +
            "      \"maxObjectSize\": 256,\n" +
            "      \"timeoutMs\": 10000\n" +
            "    }\n" +
            "  }\n" +
            "}";

        Config cfg = Config.load(writeConfig(content));

        assertNotNull(cfg);
        assertNotNull(cfg.rings);
        assertNotNull(cfg.go2java);
        assertNotNull(cfg.java2go);

        assertEquals("/tmp/go2java.mmap", cfg.go2java.filename);
        assertEquals(1024, cfg.go2java.capacity);
        assertEquals(1028, cfg.go2java.maxObjectSize);
        assertEquals(5000, cfg.go2java.timeoutMs);

        assertEquals("/tmp/java2go.mmap", cfg.java2go.filename);
        assertEquals(512, cfg.java2go.capacity);
        assertEquals(10000, cfg.java2go.timeoutMs);
    }

    @Test
    void testLoadPosixShmConfig() throws IOException {
        String content = "{\n" +
            "  \"rings\": {\n" +
            "    \"go2java\": {\n" +
            "      \"backend\": \"posix_shm\",\n" +
            "      \"shmName\": \"/go2java\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    },\n" +
            "    \"java2go\": {\n" +
            "      \"backend\": \"posix_shm\",\n" +
            "      \"shmName\": \"/java2go\",\n" +
            "      \"capacity\": 512,\n" +
            "      \"maxObjectSize\": 128\n" +
            "    }\n" +
            "  }\n" +
            "}";

        Config cfg = Config.load(writeConfig(content));

        assertEquals("posix_shm", cfg.go2java.backend);
        assertEquals("/go2java", cfg.go2java.shmName);
    }

    @Test
    void testValidateMissingRings() throws IOException {
        String content = "{}";
        assertThrows(IllegalArgumentException.class, () -> Config.load(writeConfig(content)));
    }

    @Test
    void testValidateMissingGo2Java() throws IOException {
        String content = "{\n" +
            "  \"rings\": {\n" +
            "    \"java2go\": {\n" +
            "      \"filename\": \"/tmp/java2go.mmap\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    }\n" +
            "  }\n" +
            "}";
        assertThrows(IllegalArgumentException.class, () -> Config.load(writeConfig(content)));
    }

    @Test
    void testValidateInvalidCapacity() throws IOException {
        String content = "{\n" +
            "  \"rings\": {\n" +
            "    \"go2java\": {\n" +
            "      \"filename\": \"/tmp/go2java.mmap\",\n" +
            "      \"capacity\": 0,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    },\n" +
            "    \"java2go\": {\n" +
            "      \"filename\": \"/tmp/java2go.mmap\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    }\n" +
            "  }\n" +
            "}";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> Config.load(writeConfig(content)));
        assertTrue(ex.getMessage().contains("capacity"));
    }

    @Test
    void testValidateInvalidMaxObjectSize() throws IOException {
        String content = "{\n" +
            "  \"rings\": {\n" +
            "    \"go2java\": {\n" +
            "      \"filename\": \"/tmp/go2java.mmap\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": -1\n" +
            "    },\n" +
            "    \"java2go\": {\n" +
            "      \"filename\": \"/tmp/java2go.mmap\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    }\n" +
            "  }\n" +
            "}";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> Config.load(writeConfig(content)));
        assertTrue(ex.getMessage().contains("maxObjectSize"));
    }

    @Test
    void testValidateInvalidTimeout() throws IOException {
        String content = "{\n" +
            "  \"rings\": {\n" +
            "    \"go2java\": {\n" +
            "      \"filename\": \"/tmp/go2java.mmap\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256,\n" +
            "      \"timeoutMs\": -5\n" +
            "    },\n" +
            "    \"java2go\": {\n" +
            "      \"filename\": \"/tmp/java2go.mmap\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    }\n" +
            "  }\n" +
            "}";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> Config.load(writeConfig(content)));
        assertTrue(ex.getMessage().contains("timeoutMs"));
    }

    @Test
    void testValidateMmapMissingFilename() throws IOException {
        String content = "{\n" +
            "  \"rings\": {\n" +
            "    \"go2java\": {\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    },\n" +
            "    \"java2go\": {\n" +
            "      \"filename\": \"/tmp/java2go.mmap\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    }\n" +
            "  }\n" +
            "}";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> Config.load(writeConfig(content)));
        assertTrue(ex.getMessage().contains("filename"));
    }

    @Test
    void testValidatePosixShmMissingShmName() throws IOException {
        String content = "{\n" +
            "  \"rings\": {\n" +
            "    \"go2java\": {\n" +
            "      \"backend\": \"posix_shm\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    },\n" +
            "    \"java2go\": {\n" +
            "      \"filename\": \"/tmp/java2go.mmap\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    }\n" +
            "  }\n" +
            "}";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> Config.load(writeConfig(content)));
        assertTrue(ex.getMessage().contains("shmName"));
    }

    @Test
    void testValidatePosixShmInvalidShmName() throws IOException {
        String content = "{\n" +
            "  \"rings\": {\n" +
            "    \"go2java\": {\n" +
            "      \"backend\": \"posix_shm\",\n" +
            "      \"shmName\": \"noslash\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    },\n" +
            "    \"java2go\": {\n" +
            "      \"filename\": \"/tmp/java2go.mmap\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    }\n" +
            "  }\n" +
            "}";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> Config.load(writeConfig(content)));
        assertTrue(ex.getMessage().contains("start with '/'"));
    }

    @Test
    void testValidateUnknownBackend() throws IOException {
        String content = "{\n" +
            "  \"rings\": {\n" +
            "    \"go2java\": {\n" +
            "      \"backend\": \"unknown\",\n" +
            "      \"filename\": \"/tmp/test.mmap\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    },\n" +
            "    \"java2go\": {\n" +
            "      \"filename\": \"/tmp/java2go.mmap\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    }\n" +
            "  }\n" +
            "}";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> Config.load(writeConfig(content)));
        assertTrue(ex.getMessage().contains("unknown backend"));
    }

    @Test
    void testLoadFileNotFound() {
        assertThrows(IOException.class, () -> Config.load(Path.of("/nonexistent/config.json")));
    }

    @Test
    void testLoadInvalidJson() throws IOException {
        String content = "{ not valid json";
        assertThrows(Exception.class, () -> Config.load(writeConfig(content)));
    }

    @Test
    void testBackendCaseInsensitive() throws IOException {
        String content = "{\n" +
            "  \"rings\": {\n" +
            "    \"go2java\": {\n" +
            "      \"backend\": \"MMAP\",\n" +
            "      \"filename\": \"/tmp/go2java.mmap\",\n" +
            "      \"capacity\": 1024,\n" +
            "      \"maxObjectSize\": 256\n" +
            "    },\n" +
            "    \"java2go\": {\n" +
            "      \"backend\": \"POSIX_SHM\",\n" +
            "      \"shmName\": \"/java2go\",\n" +
            "      \"capacity\": 512,\n" +
            "      \"maxObjectSize\": 128\n" +
            "    }\n" +
            "  }\n" +
            "}";

        Config cfg = Config.load(writeConfig(content));

        assertEquals("mmap", cfg.go2java.backend);
        assertEquals("posix_shm", cfg.java2go.backend);
    }
}
