//go:build !posixshm

package memory

import "fmt"

type PosixShmBackend struct{}

func OpenOrCreatePosixShm(name string, size int) (*PosixShmBackend, error) {
	return nil, fmt.Errorf("posix shm backend not enabled (build with: -tags posixshm)")
}

func (p *PosixShmBackend) Bytes() []byte { return nil }
func (p *PosixShmBackend) Size() int     { return 0 }
func (p *PosixShmBackend) Close() error  { return nil }
func (p *PosixShmBackend) Delete() error { return nil }

func UnlinkPosixShm(name string) error {
	return fmt.Errorf("posix shm backend not enabled (build with: -tags posixshm)")
}
