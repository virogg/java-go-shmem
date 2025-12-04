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

import com.jgshmem.message.NumberMessage;
import com.jgshmem.utils.Builder;

class WaitingRingProducerTest {
    @TempDir
    Path tempDir;

    private String filename;
    private WaitingRingProducer<NumberMessage> producer;

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
        producer = new WaitingRingProducer<>(16, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        assertEquals(16, producer.getCapacity());
        assertEquals(0, producer.getLastOfferedSequence());
        assertNotNull(producer.getMemory());
        assertNotNull(producer.getBuilder());
    }

    @Test
    void testDefaultCapacityConstructor() {
        producer = new WaitingRingProducer<>(NumberMessage.getMaxSize(), NumberMessage.class, filename);

        assertEquals(WaitingRingProducer.DEFAULT_CAPACITY, producer.getCapacity());
    }

    @Test
    void testNextToDispatch() {
        producer = new WaitingRingProducer<>(4, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        for (int i = 0; i < 4; i++) {
            NumberMessage msg = producer.nextToDispatch();
            assertNotNull(msg);
            msg.value = i + 1;
            assertEquals(i + 1, producer.getLastOfferedSequence());
        }

        NumberMessage msg = producer.nextToDispatch();
        assertNull(msg);
    }

    @Test
    void testFlush() {
        producer = new WaitingRingProducer<>(8, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        NumberMessage msg1 = producer.nextToDispatch();
        msg1.value = 100;

        NumberMessage msg2 = producer.nextToDispatch();
        msg2.value = 200;

        producer.flush();

        WaitingRingConsumer<NumberMessage> consumer = new WaitingRingConsumer<>(
            8, NumberMessage.getMaxSize(), NumberMessage.class, filename
        );

        assertEquals(2, consumer.availableToFetch());

        NumberMessage read1 = consumer.fetch();
        assertEquals(100, read1.value);

        NumberMessage read2 = consumer.fetch();
        assertEquals(200, read2.value);

        consumer.close(false);
    }

    @Test
    void testPowerOfTwoCapacity() {
        producer = new WaitingRingProducer<>(8, NumberMessage.getMaxSize(), NumberMessage.class, filename);
        assertTrue(producer.getCapacity() == 8);

        for (int i = 0; i < 8; i++) {
            NumberMessage msg = producer.nextToDispatch();
            msg.value = i;
        }
        producer.flush();

        producer.close(true);

        String filename2 = tempDir.resolve("producer_test2.mmap").toString();
        WaitingRingProducer<NumberMessage> producer2 = new WaitingRingProducer<>(
            5, NumberMessage.getMaxSize(), NumberMessage.class, filename2
        );

        for (int i = 0; i < 5; i++) {
            NumberMessage msg = producer2.nextToDispatch();
            msg.value = i * 10;
        }
        producer2.flush();

        WaitingRingConsumer<NumberMessage> consumer = new WaitingRingConsumer<>(
            5, NumberMessage.getMaxSize(), NumberMessage.class, filename2
        );

        for (int i = 0; i < 5; i++) {
            NumberMessage msg = consumer.fetch();
            assertEquals(i * 10, msg.value);
        }

        consumer.close(false);
        producer2.close(true);
        producer = null;
    }

    @Test
    void testObjectPooling() {
        producer = new WaitingRingProducer<>(4, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        NumberMessage msg1 = producer.nextToDispatch();
        NumberMessage msg2 = producer.nextToDispatch();

        msg1.value = 1;
        msg2.value = 2;

        producer.flush();

        WaitingRingConsumer<NumberMessage> consumer = new WaitingRingConsumer<>(
            4, NumberMessage.getMaxSize(), NumberMessage.class, filename
        );
        consumer.fetch();
        consumer.fetch();
        consumer.doneFetching();
        consumer.close(false);

        NumberMessage msg3 = producer.nextToDispatch();
        NumberMessage msg4 = producer.nextToDispatch();

        assertNotNull(msg3);
        assertNotNull(msg4);
    }

    @Test
    void testSetLastOfferedSequence() {
        producer = new WaitingRingProducer<>(16, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        producer.setLastOfferedSequence(5);
        assertEquals(5, producer.getLastOfferedSequence());
    }

    @Test
    void testBuilderConstructor() {
        Builder<NumberMessage> builder = Builder.createBuilder(NumberMessage.class);
        producer = new WaitingRingProducer<>(16, NumberMessage.getMaxSize(), builder, filename);

        assertEquals(builder, producer.getBuilder());
    }

    @Test
    void testFileCreation() {
        producer = new WaitingRingProducer<>(16, NumberMessage.getMaxSize(), NumberMessage.class, filename);

        File f = new File(filename);
        assertTrue(f.exists());

        long expectedSize = WaitingRingProducer.HEADER_SIZE + 16L * NumberMessage.getMaxSize();
        assertEquals(expectedSize, f.length());
    }
}
