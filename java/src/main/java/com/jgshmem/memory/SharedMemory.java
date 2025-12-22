package com.jgshmem.memory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import sun.misc.Unsafe;
import sun.nio.ch.FileChannelImpl;

public class SharedMemory implements Memory {
    public static final long MAX_SIZE = Long.MAX_VALUE / 2L;

    private static Unsafe unsafe;
    private static boolean UNSAFE_AVAILABLE = false;
    private static boolean MAP_UNMAP_AVAILABLE = false;
    private static boolean ADDRESS_AVAILABLE = false;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
            UNSAFE_AVAILABLE = true;
        } catch (Exception e) {
            // throw while allocating memory in constructor
        }
    }

    private static Method mmap;
    private static Method unmmap;
    private static final Field addressField;

    private static Method getMethod(Class<?> cls, String name, Class<?>... parameterTypes) throws Exception {
        Method m = cls.getDeclaredMethod(name, parameterTypes);
        m.setAccessible(true);
        return m;
    }

    static {
        Field addrField = null;

        try {
            try {
                mmap = getMethod(FileChannelImpl.class, "map0", int.class, long.class, long.class);
            } catch (Exception e) {
                try {
                    mmap = getMethod(FileChannelImpl.class, "map", int.class, long.class, long.class, boolean.class);
                } catch (Exception ex) {
                    mmap = getMethod(FileChannelImpl.class, "map", MapMode.class, long.class, long.class);
                }
            }

            try {
                unmmap = getMethod(FileChannelImpl.class, "unmap0", long.class, long.class);
            } catch (Exception e) {
                unmmap = getMethod(FileChannelImpl.class, "unmap", MappedByteBuffer.class);
            }

            MAP_UNMAP_AVAILABLE = true;

            addrField = Buffer.class.getDeclaredField("address");
            addrField.setAccessible(true);
            ADDRESS_AVAILABLE = true;
    } catch (Exception e) {
            // throw while mapping memory in constructor
        }

        addressField = addrField;
    }

    public static boolean isAvailable() {
        return UNSAFE_AVAILABLE && MAP_UNMAP_AVAILABLE && ADDRESS_AVAILABLE;
    }

    private final long address;
    private final long size;
    private final MappedByteBuffer mappedByteBuffer;
    private final String fileName;

    public SharedMemory(long size) {
        this(size, createFileName(size));
    }

    public SharedMemory(String fileName) {
        this(-1, fileName);
    }

    public SharedMemory(long size, String fileName) {
        if (!UNSAFE_AVAILABLE) {
            throw new IllegalStateException("sun.misc.Unsafe is not available");
        }

        if (!MAP_UNMAP_AVAILABLE) {
            throw new IllegalStateException("FileChannel.map/unmap is not available through reflection");
        }

        if (!ADDRESS_AVAILABLE) {
            throw new IllegalStateException("Buffer.address is not available through reflection");
        }

        if (size == -1) {
            size = findFileSize(fileName);
        } else if (size <= 0) {
            throw new IllegalArgumentException("Invalid size: " + size);
        } else if (size > MAX_SIZE) {
            throw new IllegalArgumentException("Size exceeds maximum allowed: " + size + " > " + MAX_SIZE);
        }

        this.size = size;

        try {
            int idx = fileName.lastIndexOf(File.separatorChar);
            if (idx > 0) {
                String filedir = fileName.substring(0, idx);
                File dir = new File(filedir);
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new RuntimeException("Failed to create directories: " + filedir + " for " + fileName);
                }
            }

            this.fileName = fileName;
            RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
            raf.setLength(size);
            FileChannel fc = raf.getChannel();

            Class<?>[] p = mmap.getParameterTypes();
            if (p.length == 4 && p[0] == int.class && p[3] == boolean.class) {
                this.mappedByteBuffer = null;
                this.address = (long) mmap.invoke(fc, 1, 0L, size, false);
            } else if (p.length == 3 && p[0] == int.class) {
                this.mappedByteBuffer = null;
                this.address = (long) mmap.invoke(fc, 1, 0L, size);
            } else if (p.length == 3 && p[0] == MapMode.class) {
                MappedByteBuffer mbb = (MappedByteBuffer) mmap.invoke(fc, MapMode.READ_WRITE, 0L, size);
                this.mappedByteBuffer = mbb;
                this.address = (long) addressField.get(mbb);
            } else {
                throw new IllegalStateException("Unknown mmap signature: " + mmap);
            }

            fc.close();
            raf.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to map shared memory file: " + fileName, e);
        }
    }

    public static final long findFileSize(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            throw new RuntimeException("File does not exist: " + fileName);
        }
        if (file.isDirectory()) {
            throw new RuntimeException("File is a directory: " + fileName);
        }
        return file.length();
    }

    private static final String createFileName(long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid size: " + size);
        }
        return SharedMemory.class.getSimpleName() + "-" + size + ".mmap";
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public long getPointer() {
        return address;
    }

    @Override
    public void release(boolean deleteFile) {
        RuntimeException re1 = null;

        try {
            unmmap.invoke(null, address, size);
        } catch (Exception e) {
            re1 = new RuntimeException("Cannot release mmap shared memory!", e);
            throw re1;
        } finally {
            if (deleteFile) {
                deleteFile(re1);
            }
        }
    }

    private void deleteFile(RuntimeException re1) {
        Path path = Paths.get(fileName);

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            RuntimeException re2 = new RuntimeException("Cannot delete file: " + fileName, e);

            if (re1 != null) {
                re2.addSuppressed(re1);
            }
            throw re2;
        }
    }

    @Override
    public byte getByte(long address) {
        return unsafe.getByte(address);
    }

    @Override
    public byte getByteVolatile(long address) {
        return unsafe.getByteVolatile(null, address);
    }

    @Override
    public int getInt(long address) {
        return unsafe.getInt(address);
    }

    @Override
    public int getIntVolatile(long address) {
        return unsafe.getIntVolatile(null, address);
    }

    @Override
    public long getLong(long address) {
        return unsafe.getLong(address);
    }

    @Override
    public long getLongVolatile(long address) {
        return unsafe.getLongVolatile(null, address);
    }

    @Override
    public void putByte(long address, byte value) {
        unsafe.putByte(address, value);
    }

    @Override
    public void putByteVolatile(long address, byte value) {
        unsafe.putByteVolatile(null, address, value);
    }

    @Override
    public void putInt(long address, int value) {
        unsafe.putInt(address, value);
    }

    @Override
    public void putIntVolatile(long address, int value) {
        unsafe.putIntVolatile(null, address, value);
    }

    @Override
    public void putLong(long address, long value) {
        unsafe.putLong(address, value);
    }

    @Override
    public void putLongVolatile(long address, long value) {
        unsafe.putLongVolatile(null, address, value);
    }

    @Override
    public short getShort(long address) {
        return unsafe.getShort(null, address);
    }

    @Override
    public void putShort(long address, short value) {
        unsafe.putShort(null, address, value);
    }

    @Override
    public short getShortVolatile(long address) {
        return unsafe.getShortVolatile(null, address);
    }

    @Override
    public void putShortVolatile(long address, short value) {
        unsafe.putShortVolatile(null, address, value);
    }

    @Override
    public void putByteBuffer(long address, ByteBuffer src, int length) {
        if (!src.isDirect()) {
            throw new RuntimeException("putByteBuffer can only take a direct ByteBuffer");
        }
        try {
            long srcAddress = (long) addressField.get(src);
            unsafe.copyMemory(srcAddress, address, length);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void getByteBuffer(long address, ByteBuffer dst, int length) {
        if (!dst.isDirect()) {
            throw new RuntimeException("getByteBuffer can only take a direct ByteBuffer");
        }
        try {
            long dstAddress = (long) addressField.get(dst);
            dstAddress += dst.position();
            unsafe.copyMemory(address, dstAddress, length);
            dst.position(dst.position() + length);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void putByteArray(long address, byte[] src, int length) {
        unsafe.copyMemory(src, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, address, length);
    }

    @Override
    public void getByteArray(long address, byte[] dst, int length) {
        unsafe.copyMemory(null, address, dst, Unsafe.ARRAY_BYTE_BASE_OFFSET, length);
    }
}
