package consumer

import (
	"path/filepath"
	"testing"
	"unsafe"

	ipcatomic "github.com/viroge/go-shmem/pkg/atomic"
	"github.com/viroge/go-shmem/pkg/memory"
	"github.com/viroge/go-shmem/pkg/ring"
	"github.com/viroge/go-shmem/pkg/ring/producer"

	"github.com/stretchr/testify/require"
)

func TestNewConsumerValidation(t *testing.T) {
	t.Parallel()

	t.Run("invalid maxObjectSize", func(t *testing.T) {
		_, err := New(filepath.Join(t.TempDir(), "test.mmap"), 16, 0)
		require.Error(t, err)
		require.Contains(t, err.Error(), "invalid maxObjectSize")

		_, err = New(filepath.Join(t.TempDir(), "test.mmap"), 16, -1)
		require.Error(t, err)
	})

	t.Run("invalid capacity", func(t *testing.T) {
		_, err := New(filepath.Join(t.TempDir(), "test.mmap"), 0, 64)
		require.Error(t, err)
		require.Contains(t, err.Error(), "invalid capacity")
	})

	t.Run("file not found for infer capacity", func(t *testing.T) {
		_, err := New("/nonexistent/file.mmap", -1, 64)
		require.Error(t, err)
	})
}

func TestNewConsumerWithRegionValidation(t *testing.T) {
	t.Parallel()
	
	_, err := NewWithRegion(nil, "test", 16, 64)
	require.Error(t, err)
	require.Contains(t, err.Error(), "nil region")
}

func TestConsumerBasicOps(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "mmap")
	region, err := memory.OpenOrCreateMmap(filename, ring.HeaderSize+16*64)
	require.NoError(t, err)

	c, err := NewWithRegion(region, filename, 16, 64)
	require.NoError(t, err)
	defer func() { _ = c.Close(true) }()

	require.Equal(t, 16, c.Capacity())
	require.Equal(t, uint64(0), c.LastFetchedSequence())
	require.Equal(t, uint64(0), c.LastOfferedSequence())
}

func TestConsumerNoData(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "mmap")
	c, err := New(filename, 16, 64)
	require.NoError(t, err)
	defer func() { _ = c.Close(true) }()

	require.Equal(t, int64(0), c.AvailableToFetch())

	_, err = c.Fetch(true)
	require.ErrorIs(t, err, ring.ErrNoData)

	_, err = c.Fetch(false)
	require.ErrorIs(t, err, ring.ErrNoData)
}

func TestConsumerFetch(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "fetch.mmap")
	region, err := memory.OpenOrCreateMmap(filename, ring.HeaderSize+4*64)
	require.NoError(t, err)

	c, err := NewWithRegion(region, filename, 4, 64)
	require.NoError(t, err)
	defer func() { _ = c.Close(true) }()

	base := uintptr(unsafe.Pointer(&region.Bytes()[0]))

	ipcatomic.StoreVolatileLong(base+ring.ProducerSeqOffset, 3)

	require.Equal(t, int64(3), c.AvailableToFetch())

	addr, err := c.FetchNext()
	require.NoError(t, err)
	require.NotZero(t, addr)
	require.Equal(t, uint64(1), c.LastFetchedSequence())

	addr, err = c.Fetch(false)
	require.NoError(t, err)
	require.NotZero(t, addr)
	require.Equal(t, uint64(1), c.LastFetchedSequence())

	_, _ = c.FetchNext()
	require.Equal(t, uint64(2), c.LastFetchedSequence())
}

func TestConsumerDoneFetching(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "done.mmap")
	region, err := memory.OpenOrCreateMmap(filename, ring.HeaderSize+4*64)
	require.NoError(t, err)

	c, err := NewWithRegion(region, filename, 4, 64)
	require.NoError(t, err)
	defer func() { _ = c.Close(true) }()

	base := uintptr(unsafe.Pointer(&region.Bytes()[0]))

	ipcatomic.StoreVolatileLong(base+ring.ProducerSeqOffset, 2)

	_, _ = c.FetchNext()
	_, _ = c.FetchNext()

	seq := ipcatomic.LoadVolatileLong(base + ring.ConsumerSeqOffset)
	require.Equal(t, uint64(0), seq)

	c.DoneFetching()

	seq = ipcatomic.LoadVolatileLong(base + ring.ConsumerSeqOffset)
	require.Equal(t, uint64(2), seq)
}

func TestConsumerRollback(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "rollback.mmap")
	region, err := memory.OpenOrCreateMmap(filename, ring.HeaderSize+4*64)
	require.NoError(t, err)

	c, err := NewWithRegion(region, filename, 4, 64)
	require.NoError(t, err)
	defer func() { _ = c.Close(true) }()

	base := uintptr(unsafe.Pointer(&region.Bytes()[0]))
	ipcatomic.StoreVolatileLong(base+ring.ProducerSeqOffset, 4)

	_, _ = c.FetchNext()
	_, _ = c.FetchNext()
	_, _ = c.FetchNext()
	require.Equal(t, uint64(3), c.LastFetchedSequence())

	c.RollBackN(2)
	require.Equal(t, uint64(1), c.LastFetchedSequence())

	c.RollBack()
	require.Equal(t, uint64(0), c.LastFetchedSequence())
}

func TestConsumerRollbackPanics(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "panic.mmap")
	region, err := memory.OpenOrCreateMmap(filename, ring.HeaderSize+4*64)
	require.NoError(t, err)

	c, err := NewWithRegion(region, filename, 4, 64)
	require.NoError(t, err)
	defer func() { _ = c.Close(true) }()

	base := uintptr(unsafe.Pointer(&region.Bytes()[0]))
	ipcatomic.StoreVolatileLong(base+ring.ProducerSeqOffset, 2)

	_, _ = c.FetchNext()

	require.Panics(t, func() { c.RollBackN(5) })
}

func TestConsumerIndexCalc(t *testing.T) {
	t.Parallel()

	t.Run("power of two", func(t *testing.T) {
		filename := filepath.Join(t.TempDir(), "cons_pow2.mmap")
		c, err := New(filename, 8, 64)
		require.NoError(t, err)
		defer func() { _ = c.Close(true) }()

		require.True(t, c.isPowerOfTwo)
		require.Equal(t, uint64(0), c.calcIndex(1))
		require.Equal(t, uint64(7), c.calcIndex(8))
		require.Equal(t, uint64(0), c.calcIndex(9))
	})

	t.Run("non power of two", func(t *testing.T) {
		filename := filepath.Join(t.TempDir(), "cons_nonpow2.mmap")
		c, err := New(filename, 5, 64)
		require.NoError(t, err)
		defer func() { _ = c.Close(true) }()

		require.False(t, c.isPowerOfTwo)
		require.Equal(t, uint64(0), c.calcIndex(1))
		require.Equal(t, uint64(0), c.calcIndex(6))
	})
}

func TestConsumerInferCapacity(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "infer.mmap")
	capacity := 8
	maxObjSize := 64

	p, err := producer.New(filename, capacity, maxObjSize)
	require.NoError(t, err)
	_ = p.Close(false)

	c, err := New(filename, -1, maxObjSize)
	require.NoError(t, err)
	defer func() { _ = c.Close(true) }()

	require.Equal(t, capacity, c.Capacity())
}
