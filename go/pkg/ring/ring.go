package ring

import (
	"errors"
	"fmt"

	"github.com/viroge/go-shmem/pkg/config"
	"github.com/viroge/go-shmem/pkg/memory"
)

const (
	CacheLine = 64
	SeqPrefixPadding = 24

	HeaderSize = CacheLine + CacheLine

	ProducerSeqOffset = SeqPrefixPadding
	ConsumerSeqOffset = CacheLine + SeqPrefixPadding
)

var (
	ErrRingFull = errors.New("ring full")
	ErrNoData = errors.New("no data")
)

func Init(r config.Ring) error {
	backend := backend(r)
	name, err := Name(r)
	if err != nil {
		return err
	}
	totalSize := HeaderSize + r.Capacity*r.MaxObjectSize
	region, err := memory.OpenRegion(backend, name, totalSize)
	if err != nil {
		return err
	}
	return region.Close()
}


func Reset(r config.Ring) error {
	backend := backend(r)
	name, err := Name(r)
	if err != nil {
		return err
	}
	return memory.DeleteRegion(backend, name)
}

func Name(r config.Ring) (string, error) {
	backend := backend(r)
	switch backend {
	case memory.BackendMmap:
		return r.Filename, nil
	case memory.BackendPosixShm:
		return r.ShmName, nil
	default:
		return "", fmt.Errorf("unknown backend: %q", backend)
	}
}

func backend(r config.Ring) string {
	backend := r.Backend
	if backend == "" {
		backend = memory.BackendMmap
	}
	return backend
}