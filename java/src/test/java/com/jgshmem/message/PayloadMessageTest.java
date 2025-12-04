package com.jgshmem.message;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jgshmem.memory.Memory;
import com.jgshmem.memory.SharedMemory;

class PayloadMessageTest {
    @TempDir
    Path tempDir;

    private Memory memory;

    @BeforeEach
    void setUp() {
        String filename = tempDir.resolve("message_test.mmap").toString();
        memory = new SharedMemory(1024, filename);
    }

    @AfterEach
    void tearDown() {
        if (memory != null) {
            memory.release(true);
            memory = null;
        }
    }

    @Test
    void testGetMaxSize() {
        assertEquals(68, PayloadMessage.getMaxSize(64));
        assertEquals(104, PayloadMessage.getMaxSize(100));
    }

    @Test
    void testBasicConstruction() {
        PayloadMessage msg = new PayloadMessage(64);

        assertNotNull(msg.payload);
        assertEquals(64, msg.payload.capacity());
        assertEquals(0, msg.payloadSize);
    }

    @Test
    void testWriteReadRoundtrip() {
        PayloadMessage msg1 = new PayloadMessage(64);
        msg1.payload.put("hello world".getBytes());
        msg1.payloadSize = 11;

        long addr = memory.getPointer();
        msg1.writeTo(addr, memory);

        PayloadMessage msg2 = new PayloadMessage(64);
        msg2.readFrom(addr, memory);

        assertEquals(11, msg2.payloadSize);

        byte[] result = new byte[11];
        msg2.payload.get(result);
        assertEquals("hello world", new String(result));
    }

    @Test
    void testEmptyPayload() {
        PayloadMessage msg1 = new PayloadMessage(64);
        msg1.payloadSize = 0;

        long addr = memory.getPointer();
        msg1.writeTo(addr, memory);

        PayloadMessage msg2 = new PayloadMessage(64);
        msg2.readFrom(addr, memory);

        assertEquals(0, msg2.payloadSize);
    }

    @Test
    void testMaxPayload() {
        int maxSize = 128;
        PayloadMessage msg1 = new PayloadMessage(maxSize);

        byte[] data = new byte[maxSize];
        for (int i = 0; i < maxSize; i++) {
            data[i] = (byte) (i % 256);
        }
        msg1.payload.put(data);
        msg1.payloadSize = maxSize;

        long addr = memory.getPointer();
        msg1.writeTo(addr, memory);

        PayloadMessage msg2 = new PayloadMessage(maxSize);
        msg2.readFrom(addr, memory);

        assertEquals(maxSize, msg2.payloadSize);

        byte[] result = new byte[maxSize];
        msg2.payload.get(result);
        assertArrayEquals(data, result);
    }

    @Test
    void testWriteToReturnsCorrectSize() {
        PayloadMessage msg = new PayloadMessage(64);
        msg.payload.put("test".getBytes());
        msg.payloadSize = 4;

        long addr = memory.getPointer();
        int written = msg.writeTo(addr, memory);

        assertEquals(8, written); // 4 (size) + 4 (payload)
    }

    @Test
    void testReadFromReturnsCorrectSize() {
        PayloadMessage msg1 = new PayloadMessage(64);
        msg1.payload.put("testing".getBytes());
        msg1.payloadSize = 7;

        long addr = memory.getPointer();
        msg1.writeTo(addr, memory);

        PayloadMessage msg2 = new PayloadMessage(64);
        int read = msg2.readFrom(addr, memory);

        assertEquals(11, read); // 4 (size) + 7 (payload)
    }

    @Test
    void testDirectByteBuffer() {
        PayloadMessage msg = new PayloadMessage(64);

        assertTrue(msg.payload.isDirect());
    }

    @Test
    void testMultipleMessages() {
        long addr1 = memory.getPointer();
        long addr2 = memory.getPointer() + 256;
        long addr3 = memory.getPointer() + 512;

        PayloadMessage msg1 = new PayloadMessage(64);
        msg1.payload.put("first".getBytes());
        msg1.payloadSize = 5;
        msg1.writeTo(addr1, memory);

        PayloadMessage msg2 = new PayloadMessage(64);
        msg2.payload.put("second".getBytes());
        msg2.payloadSize = 6;
        msg2.writeTo(addr2, memory);

        PayloadMessage msg3 = new PayloadMessage(64);
        msg3.payload.put("third".getBytes());
        msg3.payloadSize = 5;
        msg3.writeTo(addr3, memory);

        // Read back
        PayloadMessage read1 = new PayloadMessage(64);
        read1.readFrom(addr1, memory);
        byte[] r1 = new byte[5];
        read1.payload.get(r1);
        assertEquals("first", new String(r1));

        PayloadMessage read2 = new PayloadMessage(64);
        read2.readFrom(addr2, memory);
        byte[] r2 = new byte[6];
        read2.payload.get(r2);
        assertEquals("second", new String(r2));

        PayloadMessage read3 = new PayloadMessage(64);
        read3.readFrom(addr3, memory);
        byte[] r3 = new byte[5];
        read3.payload.get(r3);
        assertEquals("third", new String(r3));
    }
}
