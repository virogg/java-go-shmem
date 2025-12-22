#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

static void throwRuntime(JNIEnv* env, const char* msg) {
	jclass ex = (*env)->FindClass(env, "java/lang/RuntimeException");
	if (ex != NULL) {
		(*env)->ThrowNew(env, ex, msg);
	}
}

static void throwErrno(JNIEnv* env, const char* op) {
	char buf[256];
	int e = errno;
	snprintf(buf, sizeof(buf), "%s failed: errno=%d (%s)", op, e, strerror(e));
	throwRuntime(env, buf);
}

JNIEXPORT jlongArray JNICALL Java_com_jgshmem_memory_PosixShmMemory_openAndMap
  (JNIEnv* env, jclass cls, jstring jname, jlong jsize, jboolean create) {
	(void)cls;

	if (jname == NULL) {
		throwRuntime(env, "name is null");
		return NULL;
	}
	if (jsize <= 0) {
		throwRuntime(env, "invalid size");
		return NULL;
	}

	const char* name = (*env)->GetStringUTFChars(env, jname, NULL);
	if (name == NULL) return NULL;

	int flags = O_RDWR;
	if (create) flags |= O_CREAT;
	int fd = shm_open(name, flags, 0600);
	if (fd < 0) {
		(*env)->ReleaseStringUTFChars(env, jname, name);
		throwErrno(env, "shm_open");
		return NULL;
	}

	struct stat st;
	if (fstat(fd, &st) != 0) {
		(*env)->ReleaseStringUTFChars(env, jname, name);
		close(fd);
		throwErrno(env, "fstat");
		return NULL;
	}

	off_t requested = (off_t) jsize;

	if (create) {
		if (st.st_size == 0 || st.st_size < requested) {
			if (ftruncate(fd, requested) != 0) {
				(*env)->ReleaseStringUTFChars(env, jname, name);
				close(fd);
				throwErrno(env, "ftruncate");
				return NULL;
			}
			if (fstat(fd, &st) != 0) {
				(*env)->ReleaseStringUTFChars(env, jname, name);
				close(fd);
				throwErrno(env, "fstat");
				return NULL;
			}
		}
	} else if (st.st_size < requested) {
		char buf[256];
		snprintf(buf, sizeof(buf), "shm %s size %lld < requested %lld",
		         name, (long long) st.st_size, (long long) requested);
		(*env)->ReleaseStringUTFChars(env, jname, name);
		close(fd);
		throwRuntime(env, buf);
		return NULL;
	}

	jlong mapSize = (jlong) st.st_size;
	if (mapSize <= 0) {
		(*env)->ReleaseStringUTFChars(env, jname, name);
		close(fd);
		throwRuntime(env, "invalid shm size");
		return NULL;
	}

	void* addr = mmap(NULL, (size_t) mapSize, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
	if (addr == MAP_FAILED) {
		(*env)->ReleaseStringUTFChars(env, jname, name);
		close(fd);
		throwErrno(env, "mmap");
		return NULL;
	}

	jlongArray out = (*env)->NewLongArray(env, 3);
	if (out == NULL) {
		(*env)->ReleaseStringUTFChars(env, jname, name);
		munmap(addr, (size_t) mapSize);
		close(fd);
		return NULL;
	}

	jlong tmp[3];
	tmp[0] = (jlong) fd;
	tmp[1] = (jlong)(uintptr_t) addr;
	tmp[2] = mapSize;
	(*env)->SetLongArrayRegion(env, out, 0, 3, tmp);

	(*env)->ReleaseStringUTFChars(env, jname, name);
	return out;
}

JNIEXPORT void JNICALL Java_com_jgshmem_memory_PosixShmMemory_unmapAndClose
  (JNIEnv* env, jclass cls, jlong fd, jlong address, jlong size) {
	(void)cls;

	if (address != 0 && size > 0) {
		void* addr = (void*)(uintptr_t) address;
		if (munmap(addr, (size_t) size) != 0) {
			throwErrno(env, "munmap");
		}
	}

	if (fd >= 0) {
		if (close((int) fd) != 0) {
			throwErrno(env, "close");
		}
	}
}

JNIEXPORT void JNICALL Java_com_jgshmem_memory_PosixShmMemory_unlinkShm
  (JNIEnv* env, jclass cls, jstring jname) {
	(void)cls;

	if (jname == NULL) return;

	const char* name = (*env)->GetStringUTFChars(env, jname, NULL);
	if (name != NULL) {
		if (shm_unlink(name) != 0) {
			// best-effort; ignore ENOENT
			if (errno != ENOENT) {
				throwErrno(env, "shm_unlink");
			}
		}
		(*env)->ReleaseStringUTFChars(env, jname, name);
	}
}
