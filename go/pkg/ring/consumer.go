package ring

import (
	"errors"
	"fmt"
	"os"
	"unsafe"

	ipcatomic "github.com/viroge/go-shmem/pkg/atomic"
	"github.com/viroge/go-shmem/pkg/memory"
)

var ErrNoData = errors.New("no data")

type Consumer struct {
	backend memory.Region
	mem     []byte
	base    uintptr

	filename      string
	capacity      int
	maxObjectSize int

	isPowerOfTwo bool
	capMinusOne  uint64

	lastFetchedSeq uint64
	fetchCount     uint64
}

func NewConsumer(filename string, capacity int, maxObjectSize int) (*Consumer, error) {
	if maxObjectSize <= 0 {
		return nil, fmt.Errorf("invalid maxObjectSize: %d", maxObjectSize)
	}

	var totalSize int
	if capacity == -1 {
		st, err := os.Stat(filename)
		if err != nil {
			return nil, err
		}
		if st.IsDir() {
			return nil, fmt.Errorf("filename is a directory: %s", filename)
		}
		if st.Size() <= 0 {
			return nil, fmt.Errorf("cannot infer capacity from empty file: %s", filename)
		}
		totalSize = int(st.Size())
		if totalSize < HeaderSize {
			return nil, fmt.Errorf("file too small for header: %d", totalSize)
		}
		capacity = (totalSize - HeaderSize) / maxObjectSize
		if capacity <= 0 {
			return nil, fmt.Errorf("inferred invalid capacity: %d", capacity)
		}
	} else {
		if capacity <= 0 {
			return nil, fmt.Errorf("invalid capacity: %d", capacity)
		}
		totalSize = HeaderSize + capacity*maxObjectSize
	}

	backend, err := memory.OpenOrCreateMmap(filename, totalSize)
	if err != nil {
		return nil, err
	}

	return NewConsumerWithRegion(backend, filename, capacity, maxObjectSize)
}

func NewConsumerWithRegion(region memory.Region, name string, capacity int, maxObjectSize int) (*Consumer, error) {
	if region == nil {
		return nil, fmt.Errorf("nil region")
	}
	mem := region.Bytes()
	if len(mem) == 0 {
		return nil, fmt.Errorf("empty region")
	}
	base := uintptr(unsafe.Pointer(&mem[0]))

	c := &Consumer{
		backend:       region,
		mem:           mem,
		base:          base,
		filename:      name,
		capacity:      capacity,
		maxObjectSize: maxObjectSize,
		isPowerOfTwo:  (capacity & (capacity - 1)) == 0,
		capMinusOne:   uint64(capacity - 1),
	}

	c.lastFetchedSeq = ipcatomic.LoadVolatileLong(base + ConsumerSeqOffset)

	return c, nil
}

func (c *Consumer) Capacity() int {
	return c.capacity
}

func (c *Consumer) LastFetchedSequence() uint64 {
	return c.lastFetchedSeq
}

func (c *Consumer) LastOfferedSequence() uint64 {
	return ipcatomic.LoadVolatileLong(c.base + ProducerSeqOffset)
}

func (c *Consumer) AvailableToFetch() int64 {
	offered := c.LastOfferedSequence()
	return int64(offered - c.lastFetchedSeq)
}

func (c *Consumer) Fetch(remove bool) (uintptr, error) {
	if remove {
		return c.fetchTrue()
	}
	return c.fetchFalse()
}

func (c *Consumer) FetchNext() (uintptr, error) {
	return c.Fetch(true)
}

func (c *Consumer) RollBack() {
	c.RollBackN(c.fetchCount)
}

func (c *Consumer) RollBackN(count uint64) {
	if count > c.fetchCount {
		panic(fmt.Sprintf("invalid rollback: fetched=%d requested=%d", c.fetchCount, count))
	}
	c.lastFetchedSeq -= count
	c.fetchCount -= count
}

func (c *Consumer) DoneFetching() {
	ipcatomic.StoreVolatileLong(c.base+ConsumerSeqOffset, c.lastFetchedSeq)
	c.fetchCount = 0
}

func (c *Consumer) Close(deleteFile bool) error {
	var firstErr error

	if c.backend != nil {
		if err := c.backend.Close(); err != nil {
			firstErr = err
		}
		if deleteFile {
			if err := c.backend.Delete(); err != nil && firstErr == nil {
				firstErr = err
			}
		}
		c.backend = nil
	}

	return firstErr
}

func (c *Consumer) fetchTrue() (uintptr, error) {
	if c.AvailableToFetch() <= 0 {
		return 0, ErrNoData
	}

	c.fetchCount++
	c.lastFetchedSeq++

	index := c.calcIndex(c.lastFetchedSeq)
	addr := c.base + HeaderSize + uintptr(index)*uintptr(c.maxObjectSize)
	return addr, nil
}

func (c *Consumer) fetchFalse() (uintptr, error) {
	if c.AvailableToFetch() <= 0 {
		return 0, ErrNoData
	}

	next := c.lastFetchedSeq + 1
	index := c.calcIndex(next)
	addr := c.base + HeaderSize + uintptr(index)*uintptr(c.maxObjectSize)
	return addr, nil
}

func (c *Consumer) calcIndex(seq uint64) uint64 {
	v := seq - 1
	if c.isPowerOfTwo {
		return v & c.capMinusOne
	}
	return v % uint64(c.capacity)
}
