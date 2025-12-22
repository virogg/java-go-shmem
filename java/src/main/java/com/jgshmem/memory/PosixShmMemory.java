package com.jgshmem.memory;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import sun.misc.Unsafe;

public class PosixShmMemory implements Memory {
	private final long size;
	private final String name;
	private long pointer;
	private long fd;

	private static Unsafe unsafe;
	private static final long PAGE_SIZE;

	static {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			unsafe = (Unsafe) f.get(null);
			PAGE_SIZE = unsafe.pageSize();
		} catch (Exception e) {
			throw new RuntimeException("Failed to get Unsafe", e);
		}

		System.loadLibrary("java_go_ipc_posix_shm");
	}

	public PosixShmMemory(long size, String name, boolean create) {
		long alignedSize = alignToPageSize(size);
		this.name = name;
		long[] result = openAndMap(name, alignedSize, create);
		this.fd = result[0];
		this.pointer = result[1];
		this.size = result.length > 2 ? result[2] : alignedSize;
	}

	private static native long[] openAndMap(String name, long size, boolean create);
	private static native void unmapAndClose(long fd, long pointer, long size);
	private static native void unlinkShm(String name);

	@Override
	public long getPointer() {
		return pointer;
	}

	@Override
	public long getSize() {
		return size;
	}

	private static long alignToPageSize(long size) {
		if (size <= 0) {
			return size;
		}
		long rem = size % PAGE_SIZE;
		return rem == 0 ? size : size + (PAGE_SIZE - rem);
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
		if (src.isDirect()) {
			long srcAddress = ((sun.nio.ch.DirectBuffer) src).address() + src.position();
			unsafe.copyMemory(srcAddress, address, length);
		} else {
			unsafe.copyMemory(src.array(), Unsafe.ARRAY_BYTE_BASE_OFFSET + src.arrayOffset() + src.position(), null, address, length);
		}
	}

	@Override
	public void getByteBuffer(long address, ByteBuffer dst, int length) {
		if (dst.isDirect()) {
			long dstAddress = ((sun.nio.ch.DirectBuffer) dst).address() + dst.position();
			unsafe.copyMemory(null, address, null, dstAddress, length);
			dst.position(dst.position() + length);
		} else {
			unsafe.copyMemory(null, address, dst.array(), Unsafe.ARRAY_BYTE_BASE_OFFSET + dst.arrayOffset() + dst.position(), length);
			dst.position(dst.position() + length);
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

	@Override
	public void release(boolean unlink) {
		if (pointer != 0) {
			unmapAndClose(fd, pointer, size);
			if (unlink) {
				unlinkShm(name);
			}
			pointer = 0;
		}
	}
}
