package com.jgshmem.ring;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jgshmem.message.PayloadMessage;
import com.jgshmem.utils.Builder;

class WaitingRingConsumerTest {
    @TempDir
    Path tempDir;

    private static final int CAPACITY = 16;
    private static final int MAX_PAYLOAD_SIZE = 32;
    private static final int MAX_OBJECT_SIZE = PayloadMessage.getMaxSize(MAX_PAYLOAD_SIZE);

    private String filename;
    private Builder<PayloadMessage> builder;
    private WaitingRingProducer<PayloadMessage> producer;
    private WaitingRingConsumer<PayloadMessage> consumer;

    @BeforeEach
    void setUp() {
        filename = tempDir.resolve("consumer_test.mmap").toString();
        builder = payloadBuilder();
        producer = new WaitingRingProducer<>(CAPACITY, MAX_OBJECT_SIZE, builder, filename);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close(false);
            consumer = null;
        }
        if (producer != null) {
            producer.close(true);
            producer = null;
        }
    }

    @Test
    void testBasicConstruction() {
        consumer = new WaitingRingConsumer<>(CAPACITY, MAX_OBJECT_SIZE, builder, filename);

        assertEquals(CAPACITY, consumer.getCapacity());
        assertEquals(0, consumer.getLastFetchedSequence());
        assertEquals(0, consumer.getLastOfferedSequence());
        assertNotNull(consumer.getMemory());
        assertNotNull(consumer.getBuilder());
    }

    @Test
    void testDefaultCapacityConstructor() {
        String filename2 = tempDir.resolve("default.mmap").toString();
        Builder<PayloadMessage> localBuilder = payloadBuilder();
        WaitingRingProducer<PayloadMessage> p = new WaitingRingProducer<>(
            WaitingRingProducer.DEFAULT_CAPACITY, MAX_OBJECT_SIZE, localBuilder, filename2
        );

        WaitingRingConsumer<PayloadMessage> c = new WaitingRingConsumer<>(
            MAX_OBJECT_SIZE, payloadBuilder(), filename2
        );

        assertEquals(WaitingRingProducer.DEFAULT_CAPACITY, c.getCapacity());

        c.close(false);
        p.close(true);
    }

    @Test
    void testAvailableToFetch() {
        consumer = new WaitingRingConsumer<>(CAPACITY, MAX_OBJECT_SIZE, builder, filename);
        assertEquals(0, consumer.availableToFetch());

        for (int i = 0; i < 5; i++) {
            PayloadMessage msg = producer.nextToDispatch();
            writeInt(msg, i);
        }
        producer.flush();

        assertEquals(5, consumer.availableToFetch());
    }

    @Test
    void testFetchWithRemove() {
        consumer = new WaitingRingConsumer<>(CAPACITY, MAX_OBJECT_SIZE, builder, filename);

        for (int i = 0; i < 3; i++) {
            PayloadMessage msg = producer.nextToDispatch();
            writeInt(msg, (i + 1) * 100);
        }
        producer.flush();

        PayloadMessage m1 = consumer.fetch();
        assertEquals(100, readInt(m1));
        assertEquals(1, consumer.getLastFetchedSequence());

        PayloadMessage m2 = consumer.fetch(true);
        assertEquals(200, readInt(m2));
        assertEquals(2, consumer.getLastFetchedSequence());

        assertEquals(1, consumer.availableToFetch());
    }

    @Test
    void testFetchWithoutRemove() {
        consumer = new WaitingRingConsumer<>(CAPACITY, MAX_OBJECT_SIZE, builder, filename);

        PayloadMessage msg = producer.nextToDispatch();
        writeInt(msg, 42);
        producer.flush();

        PayloadMessage m1 = consumer.fetch(false);
        assertEquals(42, readInt(m1));
        assertEquals(0, consumer.getLastFetchedSequence());

        PayloadMessage m2 = consumer.fetch(false);
        assertEquals(42, readInt(m2));
        assertEquals(0, consumer.getLastFetchedSequence());

        PayloadMessage m3 = consumer.fetch(true);
        assertEquals(42, readInt(m3));
        assertEquals(1, consumer.getLastFetchedSequence());
    }

    @Test
    void testDoneFetching() {
        consumer = new WaitingRingConsumer<>(CAPACITY, MAX_OBJECT_SIZE, builder, filename);

        for (int i = 0; i < 4; i++) {
            writeInt(producer.nextToDispatch(), i);
        }
        producer.flush();

        consumer.fetch();
        consumer.fetch();
        consumer.doneFetching();

        PayloadMessage extra = producer.nextToDispatch();
        assertNotNull(extra);
        writeInt(extra, 99);
        writeInt(producer.nextToDispatch(), 100);
        producer.flush();

        assertEquals(4, consumer.availableToFetch());
    }

    @Test
    void testRollback() {
        consumer = new WaitingRingConsumer<>(CAPACITY, MAX_OBJECT_SIZE, builder, filename);

        for (int i = 0; i < 5; i++) {
            writeInt(producer.nextToDispatch(), i * 10);
        }
        producer.flush();

        consumer.fetch();
        consumer.fetch();
        consumer.fetch();
        assertEquals(3, consumer.getLastFetchedSequence());

        consumer.rollback(2);
        assertEquals(1, consumer.getLastFetchedSequence());

        PayloadMessage m = consumer.fetch();
        assertEquals(10, readInt(m));
    }

    @Test
    void testRollbackAll() {
        consumer = new WaitingRingConsumer<>(CAPACITY, MAX_OBJECT_SIZE, builder, filename);

        for (int i = 0; i < 3; i++) {
            writeInt(producer.nextToDispatch(), i);
        }
        producer.flush();

        consumer.fetch();
        consumer.fetch();
        consumer.fetch();
        assertEquals(3, consumer.getLastFetchedSequence());

        consumer.rollback();
        assertEquals(0, consumer.getLastFetchedSequence());
    }

    @Test
    void testRollbackInvalidCount() {
        consumer = new WaitingRingConsumer<>(CAPACITY, MAX_OBJECT_SIZE, builder, filename);

        writeInt(producer.nextToDispatch(), 1);
        producer.flush();

        consumer.fetch();

        assertThrows(RuntimeException.class, () -> consumer.rollback(5));
        assertThrows(RuntimeException.class, () -> consumer.rollback(-1));
    }

    @Test
    void testInferCapacityFromFile() {
        producer.close(false);

        consumer = new WaitingRingConsumer<>(-1, MAX_OBJECT_SIZE, builder, filename);

        assertEquals(CAPACITY, consumer.getCapacity());
    }

    @Test
    void testInferCapacityFileNotFound() {
        String badFile = tempDir.resolve("nonexistent.mmap").toString();

        assertThrows(RuntimeException.class, () -> {
            new WaitingRingConsumer<>(-1, MAX_OBJECT_SIZE, builder, badFile);
        });
    }

    @Test
    void testSetLastFetchedSequence() {
        consumer = new WaitingRingConsumer<>(CAPACITY, MAX_OBJECT_SIZE, builder, filename);

        consumer.setLastFetchedSequence(10);
        assertEquals(10, consumer.getLastFetchedSequence());
    }

    @Test
    void testBuilderConstructor() {
        Builder<PayloadMessage> customBuilder = payloadBuilder();
        consumer = new WaitingRingConsumer<>(CAPACITY, MAX_OBJECT_SIZE, customBuilder, filename);

        assertEquals(customBuilder, consumer.getBuilder());
    }

    @Test
    void testNonPowerOfTwoCapacity() {
        String filename2 = tempDir.resolve("nonpow2.mmap").toString();

        Builder<PayloadMessage> localBuilder = payloadBuilder();
        WaitingRingProducer<PayloadMessage> p = new WaitingRingProducer<>(
            5, MAX_OBJECT_SIZE, localBuilder, filename2
        );
        WaitingRingConsumer<PayloadMessage> c = new WaitingRingConsumer<>(
            5, MAX_OBJECT_SIZE, localBuilder, filename2
        );

        for (int i = 0; i < 5; i++) {
            writeInt(p.nextToDispatch(), i * 100);
        }
        p.flush();

        for (int i = 0; i < 5; i++) {
            PayloadMessage m = c.fetch();
            assertEquals(i * 100, readInt(m));
        }

        c.close(false);
        p.close(true);
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
