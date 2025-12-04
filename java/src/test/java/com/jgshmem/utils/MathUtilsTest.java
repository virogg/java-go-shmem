package com.jgshmem.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class MathUtilsTest {

    @Test
    void testIsPowerOfTwoInt() {
        assertTrue(MathUtils.isPowerOfTwo(1));
        assertTrue(MathUtils.isPowerOfTwo(2));
        assertTrue(MathUtils.isPowerOfTwo(4));
        assertTrue(MathUtils.isPowerOfTwo(8));
        assertTrue(MathUtils.isPowerOfTwo(16));
        assertTrue(MathUtils.isPowerOfTwo(1024));

        assertFalse(MathUtils.isPowerOfTwo(0));
        assertFalse(MathUtils.isPowerOfTwo(-1));
        assertFalse(MathUtils.isPowerOfTwo(-2));
        assertFalse(MathUtils.isPowerOfTwo(3));
        assertFalse(MathUtils.isPowerOfTwo(5));
        assertFalse(MathUtils.isPowerOfTwo(6));
        assertFalse(MathUtils.isPowerOfTwo(7));
        assertFalse(MathUtils.isPowerOfTwo(1000));
    }

    @Test
    void testIsPowerOfTwoLong() {
        assertTrue(MathUtils.isPowerOfTwo(1L));
        assertTrue(MathUtils.isPowerOfTwo(2L));
        assertTrue(MathUtils.isPowerOfTwo(1L << 30));
        assertTrue(MathUtils.isPowerOfTwo(1L << 40));

        assertFalse(MathUtils.isPowerOfTwo(0L));
        assertFalse(MathUtils.isPowerOfTwo(-1L));
        assertFalse(MathUtils.isPowerOfTwo(3L));
        assertFalse(MathUtils.isPowerOfTwo((1L << 30) + 1));
    }

    @Test
    void testEnsurePowerOfTwoIntValid() {
        assertDoesNotThrow(() -> MathUtils.ensurePowerOfTwo(1));
        assertDoesNotThrow(() -> MathUtils.ensurePowerOfTwo(2));
        assertDoesNotThrow(() -> MathUtils.ensurePowerOfTwo(4));
        assertDoesNotThrow(() -> MathUtils.ensurePowerOfTwo(1024));
    }

    @Test
    void testEnsurePowerOfTwoIntInvalid() {
        assertThrows(IllegalArgumentException.class, () -> MathUtils.ensurePowerOfTwo(0));
        assertThrows(IllegalArgumentException.class, () -> MathUtils.ensurePowerOfTwo(-1));
        assertThrows(IllegalArgumentException.class, () -> MathUtils.ensurePowerOfTwo(3));
        assertThrows(IllegalArgumentException.class, () -> MathUtils.ensurePowerOfTwo(5));
    }

    @Test
    void testEnsurePowerOfTwoLongValid() {
        assertDoesNotThrow(() -> MathUtils.ensurePowerOfTwo(1L));
        assertDoesNotThrow(() -> MathUtils.ensurePowerOfTwo(1L << 40));
    }

    @Test
    void testEnsurePowerOfTwoLongInvalid() {
        assertThrows(IllegalArgumentException.class, () -> MathUtils.ensurePowerOfTwo(0L));
        assertThrows(IllegalArgumentException.class, () -> MathUtils.ensurePowerOfTwo(-1L));
        assertThrows(IllegalArgumentException.class, () -> MathUtils.ensurePowerOfTwo(3L));
    }

    @Test
    void testEdgeCases() {
        assertTrue(MathUtils.isPowerOfTwo(1 << 30));
        assertTrue(MathUtils.isPowerOfTwo(1L << 62));
        assertFalse(MathUtils.isPowerOfTwo(1023)); // 1024 - 1
        assertFalse(MathUtils.isPowerOfTwo(2047)); // 2048 - 1
        assertFalse(MathUtils.isPowerOfTwo(1025)); // 1024 + 1
    }
}
