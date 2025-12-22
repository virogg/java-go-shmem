package com.jgshmem.memory;

public class PosixShmTest {
	public static void main(String[] args) {
		String shmName = "/test_posix_shm";
		long size = 1024;

		System.out.println("Testing POSIX shm: " + shmName + " size=" + size);

		try {
			System.out.println("Creating shared memory...");
			PosixShmMemory mem = new PosixShmMemory(size, shmName, true);
			System.out.println("Created. Pointer: 0x" + Long.toHexString(mem.getPointer()));

			long addr = mem.getPointer();
			mem.putLong(addr, 0x1122334455667788L);
			mem.putInt(addr + 8, 0xAABBCCDD);
			mem.putByte(addr + 12, (byte) 0x42);
			System.out.println("Wrote data");

			long val1 = mem.getLong(addr);
			int val2 = mem.getInt(addr + 8);
			byte val3 = mem.getByte(addr + 12);

			System.out.println("Read back:");
			System.out.println("  Long:  0x" + Long.toHexString(val1));
			System.out.println("  Int:   0x" + Integer.toHexString(val2));
			System.out.println("  Byte:  0x" + Integer.toHexString(val3 & 0xFF));

			boolean success = (val1 == 0x1122334455667788L) &&
			                  (val2 == 0xAABBCCDD) &&
			                  (val3 == 0x42);

			mem.release(true);
			System.out.println("Cleaned up");

			if (success) {
				System.out.println("SUCCESS: POSIX shm test passed!");
			} else {
				System.out.println("FAILURE: Data mismatch");
				System.exit(1);
			}

		} catch (Exception e) {
			System.err.println("ERROR: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}
}
