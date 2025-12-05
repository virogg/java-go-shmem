package memory

import (
	"errors"
	"fmt"
	"os"
)

const (
	BackendMmap     = "mmap"
	BackendPosixShm = "posix_shm"
)

func OpenRegion(backend string, name string, size int) (Region, error) {
	switch backend {
	case "", BackendMmap:
		return OpenOrCreateMmap(name, size)
	case BackendPosixShm:
		return OpenOrCreatePosixShm(name, size)
	default:
		return nil, fmt.Errorf("unknown backend: %q", backend)
	}
}

func DeleteRegion(backend string, name string) error {
	switch backend {
	case "", BackendMmap:
		if err := os.Remove(name); err != nil && !errors.Is(err, os.ErrNotExist) {
			return err
		}
		return nil
	case BackendPosixShm:
		return UnlinkPosixShm(name)
	default:
		return fmt.Errorf("unknown backend: %q", backend)
	}
}

