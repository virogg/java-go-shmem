package memory

import (
	"errors"
	"fmt"
	"os"
	"syscall"
)

type MmapBackend struct {
	file *os.File
	data []byte
	size int
	path string
}

func OpenOrCreateMmap(filename string, size int) (*MmapBackend, error) {
	if size <= 0 {
		return nil, fmt.Errorf("invalid size: %d", size)
	}

	file, err := os.OpenFile(filename, os.O_RDWR|os.O_CREATE, 0o600)
	if err != nil {
		return nil, err
	}

	if err := file.Truncate(int64(size)); err != nil {
		_ = file.Close()
		return nil, err
	}

	data, err := syscall.Mmap(int(file.Fd()), 0, size, syscall.PROT_READ|syscall.PROT_WRITE, syscall.MAP_SHARED)
	if err != nil {
		_ = file.Close()
		return nil, err
	}

	return &MmapBackend{
		file: file,
		data: data,
		size: size,
		path: filename,
	}, nil
}

func (m *MmapBackend) Bytes() []byte {
	return m.data
}

func (m *MmapBackend) Size() int {
	return m.size
}

func (m *MmapBackend) Close() error {
	var firstErr error

	if m.data != nil {
		if err := syscall.Munmap(m.data); err != nil {
			firstErr = err
		}
		m.data = nil
	}

	if m.file != nil {
		if err := m.file.Close(); err != nil && firstErr == nil {
			firstErr = err
		}
		m.file = nil
	}

	return firstErr
}

func (m *MmapBackend) Delete() error {
	if m.path == "" {
		return nil
	}
	if err := os.Remove(m.path); err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	return nil
}
