package producer

import (
	"fmt"
	"unsafe"

	ipcatomic "github.com/viroge/go-shmem/pkg/atomic"
	"github.com/viroge/go-shmem/pkg/config"
	"github.com/viroge/go-shmem/pkg/memory"
	"github.com/viroge/go-shmem/pkg/ring"
)



type Producer struct {
	backend memory.Region
	mem     []byte
	base    uintptr

	filename      string
	capacity      int
	maxObjectSize int

	isPowerOfTwo   bool
	capMinusOne    uint64
	lastOfferedSeq uint64
	maxSeqBeforeWr uint64
	pendingCount   uint64
}

func New(filename string, capacity int, maxObjectSize int) (*Producer, error) {
	if capacity <= 0 {
		return nil, fmt.Errorf("invalid capacity: %d", capacity)
	}
	if maxObjectSize <= 0 {
		return nil, fmt.Errorf("invalid maxObjectSize: %d", maxObjectSize)
	}

	totalSize := ring.HeaderSize + capacity*maxObjectSize

	backend, err := memory.OpenOrCreateMmap(filename, totalSize)
	if err != nil {
		return nil, err
	}

	return NewWithRegion(backend, filename, capacity, maxObjectSize)
}

func NewWithRegion(region memory.Region, name string, capacity int, maxObjectSize int) (*Producer, error) {
	if region == nil {
		return nil, fmt.Errorf("nil region")
	}
	mem := region.Bytes()
	if len(mem) == 0 {
		return nil, fmt.Errorf("empty region")
	}
	base := uintptr(unsafe.Pointer(&mem[0]))

	p := &Producer{
		backend:       region,
		mem:           mem,
		base:          base,
		filename:      name,
		capacity:      capacity,
		maxObjectSize: maxObjectSize,
		isPowerOfTwo:  (capacity & (capacity - 1)) == 0,
		capMinusOne:   uint64(capacity - 1),
	}

	p.lastOfferedSeq = ipcatomic.LoadVolatileLong(base + ring.ProducerSeqOffset)
	p.maxSeqBeforeWr = ipcatomic.LoadVolatileLong(base + ring.ConsumerSeqOffset) + uint64(capacity)

	return p, nil
}

func Open(r config.Ring) (*Producer, error) {
	backend := r.Backend
	if backend == "" {
		backend = memory.BackendMmap
	}
	name, err := ring.Name(r)
	if err != nil {
		return nil, err
	}

	totalSize := ring.HeaderSize + r.Capacity*r.MaxObjectSize
	region, err := memory.OpenRegion(backend, name, totalSize)
	if err != nil {
		return nil, err
	}

	return NewWithRegion(region, name, r.Capacity, r.MaxObjectSize)
}

func (p *Producer) Capacity() int {
	return p.capacity
}

func (p *Producer) LastOfferedSequence() uint64 {
	return p.lastOfferedSeq
}

func (p *Producer) NextToDispatch() (uintptr, error) {
	nextSeq := p.lastOfferedSeq + 1

	if nextSeq > p.maxSeqBeforeWr {
		p.maxSeqBeforeWr = ipcatomic.LoadVolatileLong(p.base+ring.ConsumerSeqOffset) + uint64(p.capacity)
		if nextSeq > p.maxSeqBeforeWr {
			return 0, ring.ErrRingFull
		}
	}

	p.lastOfferedSeq = nextSeq
	p.pendingCount++

	index := p.calcIndex(nextSeq)
	addr := p.base + ring.HeaderSize + uintptr(index)*uintptr(p.maxObjectSize)
	return addr, nil
}

func (p *Producer) Flush() {
	ipcatomic.StoreVolatileLong(p.base+ring.ProducerSeqOffset, p.lastOfferedSeq)
	p.pendingCount = 0
}

func (p *Producer) Close(deleteFile bool) error {
	var firstErr error

	if p.backend != nil {
		if err := p.backend.Close(); err != nil {
			firstErr = err
		}
		if deleteFile {
			if err := p.backend.Delete(); err != nil && firstErr == nil {
				firstErr = err
			}
		}
		p.backend = nil
	}

	return firstErr
}

func (p *Producer) calcIndex(seq uint64) uint64 {
	v := seq - 1
	if p.isPowerOfTwo {
		return v & p.capMinusOne
	}
	return v % uint64(p.capacity)
}
