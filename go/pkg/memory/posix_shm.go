//go:build (linux || darwin) && cgo && posixshm

package memory

/*
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

static int goipc_shm_open(const char* name, int create) {
	int flags = O_RDWR;
	if (create) flags |= O_CREAT;
	return shm_open(name, flags, 0600);
}

static int goipc_errno(void) {
	return errno;
}

static const char* goipc_strerror(int e) {
	return strerror(e);
}
*/
import "C"

import (
	"fmt"
	"os"
	"unsafe"
)

type PosixShmBackend struct {
	name string
	fd   C.int
	data []byte
	size int
}

func OpenOrCreatePosixShm(name string, size int) (*PosixShmBackend, error) {
	if size <= 0 {
		return nil, fmt.Errorf("invalid size: %d", size)
	}
	if name == "" || name[0] != '/' {
		return nil, fmt.Errorf("invalid shm name %q (must start with '/')", name)
	}

	targetSize := alignToPageSize(size)

	cname := C.CString(name)
	defer C.free(unsafe.Pointer(cname))

	fd := C.goipc_shm_open(cname, 0)
	needsTruncate := false
	mapSize := 0

	if fd < 0 {
		fd = C.goipc_shm_open(cname, 1)
		if fd < 0 {
			return nil, fmt.Errorf("shm_open(%s): %w", name, errnoErr("shm_open"))
		}
		needsTruncate = true
	} else {
		var stat C.struct_stat
		if C.fstat(fd, &stat) != 0 {
			_ = C.close(fd)
			return nil, fmt.Errorf("fstat(%s): %w", name, errnoErr("fstat"))
		}
		mapSize = int(stat.st_size)
		if mapSize < size {
			_ = C.close(fd)
			return nil, fmt.Errorf("shm object %s exists with size %d, expected at least %d", name, stat.st_size, size)
		}
	}

	if needsTruncate {
		if C.ftruncate(fd, C.off_t(targetSize)) != 0 {
			_ = C.close(fd)
			return nil, fmt.Errorf("ftruncate(%s): %w", name, errnoErr("ftruncate"))
		}
		var stat C.struct_stat
		if C.fstat(fd, &stat) != 0 {
			_ = C.close(fd)
			return nil, fmt.Errorf("fstat(%s): %w", name, errnoErr("fstat"))
		}
		mapSize = int(stat.st_size)
	}

	if mapSize == 0 {
		mapSize = targetSize
	}

	addr := C.mmap(nil, C.size_t(mapSize), C.PROT_READ|C.PROT_WRITE, C.MAP_SHARED, fd, 0)
	if addr == C.MAP_FAILED {
		_ = C.close(fd)
		return nil, fmt.Errorf("mmap(%s): %w", name, errnoErr("mmap"))
	}

	data := unsafe.Slice((*byte)(addr), mapSize)
	return &PosixShmBackend{name: name, fd: fd, data: data, size: mapSize}, nil
}

func (p *PosixShmBackend) Bytes() []byte { return p.data }
func (p *PosixShmBackend) Size() int     { return p.size }

func (p *PosixShmBackend) Close() error {
	var firstErr error

	if p.data != nil {
		if C.munmap(unsafe.Pointer(&p.data[0]), C.size_t(p.size)) != 0 && firstErr == nil {
			firstErr = errnoErr("munmap")
		}
		p.data = nil
	}

	if p.fd >= 0 {
		if C.close(p.fd) != 0 && firstErr == nil {
			firstErr = errnoErr("close")
		}
		p.fd = -1
	}

	return firstErr
}

func (p *PosixShmBackend) Delete() error {
	cname := C.CString(p.name)
	defer C.free(unsafe.Pointer(cname))
	if C.shm_unlink(cname) != 0 {
		if errno() == int(C.ENOENT) {
			return nil
		}
		return errnoErr("shm_unlink")
	}
	return nil
}

func UnlinkPosixShm(name string) error {
	if name == "" || name[0] != '/' {
		return fmt.Errorf("invalid shm name %q (must start with '/')", name)
	}
	cname := C.CString(name)
	defer C.free(unsafe.Pointer(cname))
	if C.shm_unlink(cname) != 0 {
		if errno() == int(C.ENOENT) {
			return nil
		}
		return errnoErr("shm_unlink")
	}
	return nil
}

func errnoErr(op string) error {
	e := errno()
	return fmt.Errorf("%s: errno=%d (%s)", op, e, C.GoString(C.goipc_strerror(C.int(e))))
}

func errno() int {
	return int(C.goipc_errno())
}

// alignToPageSize rounds up to the OS page size so ftruncate/fstat agree (e.g., macOS 16KB pages).
func alignToPageSize(size int) int {
	page := os.Getpagesize()
	if page <= 0 {
		return size
	}
	rem := size % page
	if rem == 0 {
		return size
	}
	return size + (page - rem)
}
