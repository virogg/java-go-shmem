package com.jgshmem.utils;

public class MathUtils {
    private MathUtils() {}
    
    public static boolean isPowerOfTwo(int n) {
        return isPowerOfTwo((long) n);
    }

    public static boolean isPowerOfTwo(long n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    public static final void ensurePowerOfTwo(int n) {
        ensurePowerOfTwo((long) n);
    }

    public static final void ensurePowerOfTwo(long n) {
        if (!isPowerOfTwo(n)) {
            throw new IllegalArgumentException("Not a power of two: " + n);
        }
    }
}
