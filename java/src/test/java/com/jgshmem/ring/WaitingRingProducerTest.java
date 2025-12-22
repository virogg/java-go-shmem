package com.jgshmem.ring;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jgshmem.message.PayloadMessage;
import com.jgshmem.utils.Builder;

class WaitingRingProducerTest {
    @TempDir
    Path tempDir;

    private static final int CAPACITY = 16;
    private static final int MAX_PAYLOAD_SIZE = 32;
    private static final int MAX_OBJECT_SIZE = PayloadMessage.getMaxSize(MAX_PAYLOAD_SIZE);

    private String filename;
    private WaitingRingProducer<PayloadMessage> producer;

    @BeforeEach
    void setUp() {
        filename = tempDir.resolve("producer_test.mmap").toString();
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.close(true);
            producer = null;
        }
    }

    @Test
    void testBasicConstruction() {
        Builder<PayloadMessage> builder = payloadBuilder();
        producer = new WaitingRingProducer<>(CAPACITY, MAX_OBJECT_SIZE, builder, filename);

        assertEquals(CAPACITY, producer.getCapacity());
        assertEquals(0, producer.getLastOfferedSequence());
        assertNotNull(producer.getMemory());
        assertNotNull(producer.getBuilder());
    }

    @Test
    void testDefaultCapacityConstructor() {
        producer = new WaitingRingProducer<>(MAX_OBJECT_SIZE, payloadBuilder(), filename);

        assertEquals(WaitingRingProducer.DEFAULT_CAPACITY, producer.getCapacity());
    }

    @Test
    void testNextToDispatch() {
        producer = new WaitingRingProducer<>(4, MAX_OBJECT_SIZE, payloadBuilder(), filename);

        for (int i = 0; i < 4; i++) {
            PayloadMessage msg = producer.nextToDispatch();
            assertNotNull(msg);
            writeInt(msg, i + 1);
            assertEquals(i + 1, producer.getLastOfferedSequence());
        }

        PayloadMessage msg = producer.nextToDispatch();
        assertNull(msg);
    }

    @Test
    void testFlush() {
        Builder<PayloadMessage> builder = payloadBuilder();
        producer = new WaitingRingProducer<>(8, MAX_OBJECT_SIZE, builder, filename);

        PayloadMessage msg1 = producer.nextToDispatch();
        writeInt(msg1, 100);

        PayloadMessage msg2 = producer.nextToDispatch();
        writeInt(msg2, 200);

        producer.flush();

        WaitingRingConsumer<PayloadMessage> consumer = new WaitingRingConsumer<>(
            8, MAX_OBJECT_SIZE, builder, filename
        );

        assertEquals(2, consumer.availableToFetch());

        PayloadMessage read1 = consumer.fetch();
        assertEquals(100, readInt(read1));

        PayloadMessage read2 = consumer.fetch();
        assertEquals(200, readInt(read2));

        consumer.close(false);
    }

    @Test
    void testPowerOfTwoCapacity() {
        Builder<PayloadMessage> builder = payloadBuilder();
        producer = new WaitingRingProducer<>(8, MAX_OBJECT_SIZE, builder, filename);
        assertTrue(producer.getCapacity() == 8);

        for (int i = 0; i < 8; i++) {
            PayloadMessage msg = producer.nextToDispatch();
            writeInt(msg, i);
        }
        producer.flush();

        producer.close(true);

        String filename2 = tempDir.resolve("producer_test2.mmap").toString();
        Builder<PayloadMessage> builder2 = payloadBuilder();
        WaitingRingProducer<PayloadMessage> producer2 = new WaitingRingProducer<>(
            5, MAX_OBJECT_SIZE, builder2, filename2
        );

        for (int i = 0; i < 5; i++) {
            PayloadMessage msg = producer2.nextToDispatch();
            writeInt(msg, i * 10);
        }
        producer2.flush();

        WaitingRingConsumer<PayloadMessage> consumer = new WaitingRingConsumer<>(
            5, MAX_OBJECT_SIZE, builder2, filename2
        );

        for (int i = 0; i < 5; i++) {
            PayloadMessage msg = consumer.fetch();
            assertEquals(i * 10, readInt(msg));
        }

        consumer.close(false);
        producer2.close(true);
        producer = null;
    }

    @Test
    void testObjectPooling() {
        producer = new WaitingRingProducer<>(4, MAX_OBJECT_SIZE, payloadBuilder(), filename);

        PayloadMessage msg1 = producer.nextToDispatch();
        PayloadMessage msg2 = producer.nextToDispatch();

        writeInt(msg1, 1);
        writeInt(msg2, 2);

        producer.flush();

        WaitingRingConsumer<PayloadMessage> consumer = new WaitingRingConsumer<>(
            4, MAX_OBJECT_SIZE, payloadBuilder(), filename
        );
        consumer.fetch();
        consumer.fetch();
        consumer.doneFetching();
        consumer.close(false);

        PayloadMessage msg3 = producer.nextToDispatch();
        PayloadMessage msg4 = producer.nextToDispatch();

        assertNotNull(msg3);
        assertNotNull(msg4);
    }

    @Test
    void testSetLastOfferedSequence() {
        producer = new WaitingRingProducer<>(CAPACITY, MAX_OBJECT_SIZE, payloadBuilder(), filename);

        producer.setLastOfferedSequence(5);
        assertEquals(5, producer.getLastOfferedSequence());
    }

    @Test
    void testBuilderConstructor() {
        Builder<PayloadMessage> builder = payloadBuilder();
        producer = new WaitingRingProducer<>(CAPACITY, MAX_OBJECT_SIZE, builder, filename);

        assertEquals(builder, producer.getBuilder());
    }

    @Test
    void testFileCreation() {
        producer = new WaitingRingProducer<>(CAPACITY, MAX_OBJECT_SIZE, payloadBuilder(), filename);

        File f = new File(filename);
        assertTrue(f.exists());

        long expectedSize = WaitingRingProducer.HEADER_SIZE + 16L * MAX_OBJECT_SIZE;
        assertEquals(expectedSize, f.length());
    }

    private Builder<PayloadMessage> payloadBuilder() {
        return () -> new PayloadMessage(MAX_PAYLOAD_SIZE);
    }

    private void writeInt(PayloadMessage msg, int value) {
        msg.payload.clear();
        msg.payload.putInt(value);
        msg.payloadSize = Integer.BYTES;
    }

    private int readInt(PayloadMessage msg) {
        return msg.payload.getInt(0);
    }
}
