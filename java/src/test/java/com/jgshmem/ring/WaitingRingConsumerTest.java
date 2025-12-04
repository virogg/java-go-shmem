package com.jgshmem.ring;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jgshmem.message.NumberMessage;
import com.jgshmem.utils.Builder;

class WaitingRingConsumerTest {
    @TempDir
    Path tempDir;

    private String filename;
    private WaitingRingProducer<NumberMessage> producer;
    private WaitingRingConsumer<NumberMessage> consumer;

    @BeforeEach
    void setUp() {
        filename = tempDir.resolve("consumer_test.mmap").toString();
        producer = new WaitingRingProducer<>(16, NumberMessage.getMaxSize(), NumberMessage.class, filename);
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
        consumer = new WaitingRingConsumer<>(16, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        assertEquals(16, consumer.getCapacity());
        assertEquals(0, consumer.getLastFetchedSequence());
        assertEquals(0, consumer.getLastOfferedSequence());
        assertNotNull(consumer.getMemory());
        assertNotNull(consumer.getBuilder());
    }

    @Test
    void testDefaultCapacityConstructor() {
        String filename2 = tempDir.resolve("default.mmap").toString();
        WaitingRingProducer<NumberMessage> p = new WaitingRingProducer<>(
            WaitingRingProducer.DEFAULT_CAPACITY, NumberMessage.getMaxSize(), NumberMessage.class, filename2
        );

        WaitingRingConsumer<NumberMessage> c = new WaitingRingConsumer<>(
            NumberMessage.getMaxSize(), NumberMessage.class, filename2
        );

        assertEquals(WaitingRingProducer.DEFAULT_CAPACITY, c.getCapacity());

        c.close(false);
        p.close(true);
    }

    @Test
    void testAvailableToFetch() {
        consumer = new WaitingRingConsumer<>(16, NumberMessage.getMaxSize(), NumberMessage.class, filename);
        assertEquals(0, consumer.availableToFetch());

        for (int i = 0; i < 5; i++) {
            NumberMessage msg = producer.nextToDispatch();
            msg.value = i;
        }
        producer.flush();

        assertEquals(5, consumer.availableToFetch());
    }

    @Test
    void testFetchWithRemove() {
        consumer = new WaitingRingConsumer<>(16, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        for (int i = 0; i < 3; i++) {
            NumberMessage msg = producer.nextToDispatch();
            msg.value = (i + 1) * 100;
        }
        producer.flush();

        NumberMessage m1 = consumer.fetch();
        assertEquals(100, m1.value);
        assertEquals(1, consumer.getLastFetchedSequence());

        NumberMessage m2 = consumer.fetch(true);
        assertEquals(200, m2.value);
        assertEquals(2, consumer.getLastFetchedSequence());

        assertEquals(1, consumer.availableToFetch());
    }

    @Test
    void testFetchWithoutRemove() {
        consumer = new WaitingRingConsumer<>(16, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        NumberMessage msg = producer.nextToDispatch();
        msg.value = 42;
        producer.flush();

        NumberMessage m1 = consumer.fetch(false);
        assertEquals(42, m1.value);
        assertEquals(0, consumer.getLastFetchedSequence());

        NumberMessage m2 = consumer.fetch(false);
        assertEquals(42, m2.value);
        assertEquals(0, consumer.getLastFetchedSequence());

        NumberMessage m3 = consumer.fetch(true);
        assertEquals(42, m3.value);
        assertEquals(1, consumer.getLastFetchedSequence());
    }

    @Test
    void testDoneFetching() {
        consumer = new WaitingRingConsumer<>(16, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        for (int i = 0; i < 4; i++) {
            producer.nextToDispatch().value = i;
        }
        producer.flush();

        consumer.fetch();
        consumer.fetch();
        consumer.doneFetching();

        NumberMessage extra = producer.nextToDispatch();
        assertNotNull(extra);
        extra.value = 99;
        producer.nextToDispatch().value = 100;
        producer.flush();

        assertEquals(4, consumer.availableToFetch());
    }

    @Test
    void testRollback() {
        consumer = new WaitingRingConsumer<>(16, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        for (int i = 0; i < 5; i++) {
            producer.nextToDispatch().value = i * 10;
        }
        producer.flush();

        consumer.fetch();
        consumer.fetch();
        consumer.fetch();
        assertEquals(3, consumer.getLastFetchedSequence());

        consumer.rollback(2);
        assertEquals(1, consumer.getLastFetchedSequence());

        NumberMessage m = consumer.fetch();
        assertEquals(10, m.value);
    }

    @Test
    void testRollbackAll() {
        consumer = new WaitingRingConsumer<>(16, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        for (int i = 0; i < 3; i++) {
            producer.nextToDispatch().value = i;
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
        consumer = new WaitingRingConsumer<>(16, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        producer.nextToDispatch().value = 1;
        producer.flush();

        consumer.fetch();

        assertThrows(RuntimeException.class, () -> consumer.rollback(5));
        assertThrows(RuntimeException.class, () -> consumer.rollback(-1));
    }

    @Test
    void testInferCapacityFromFile() {
        producer.close(false);

        consumer = new WaitingRingConsumer<>(-1, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        assertEquals(16, consumer.getCapacity());
    }

    @Test
    void testInferCapacityFileNotFound() {
        String badFile = tempDir.resolve("nonexistent.mmap").toString();

        assertThrows(RuntimeException.class, () -> {
            new WaitingRingConsumer<>(-1, NumberMessage.getMaxSize(), NumberMessage.class, badFile);
        });
    }

    @Test
    void testSetLastFetchedSequence() {
        consumer = new WaitingRingConsumer<>(16, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        consumer.setLastFetchedSequence(10);
        assertEquals(10, consumer.getLastFetchedSequence());
    }

    @Test
    void testBuilderConstructor() {
        Builder<NumberMessage> builder = Builder.createBuilder(NumberMessage.class);
        consumer = new WaitingRingConsumer<>(16, NumberMessage.getMaxSize(), builder, filename);

        assertEquals(builder, consumer.getBuilder());
    }

    @Test
    void testNonPowerOfTwoCapacity() {
        String filename2 = tempDir.resolve("nonpow2.mmap").toString();

        WaitingRingProducer<NumberMessage> p = new WaitingRingProducer<>(
            5, NumberMessage.getMaxSize(), NumberMessage.class, filename2
        );
        WaitingRingConsumer<NumberMessage> c = new WaitingRingConsumer<>(
            5, NumberMessage.getMaxSize(), NumberMessage.class, filename2
        );

        for (int i = 0; i < 5; i++) {
            p.nextToDispatch().value = i * 100;
        }
        p.flush();

        for (int i = 0; i < 5; i++) {
            NumberMessage m = c.fetch();
            assertEquals(i * 100, m.value);
        }

        c.close(false);
        p.close(true);
    }
}
