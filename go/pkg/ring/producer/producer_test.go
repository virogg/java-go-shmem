package producer

import (
	"path/filepath"
	"testing"
	"unsafe"

	ipcatomic "github.com/viroge/go-shmem/pkg/atomic"
	"github.com/viroge/go-shmem/pkg/memory"
	"github.com/viroge/go-shmem/pkg/ring"

	"github.com/stretchr/testify/require"
)

func TestNewProducerValidation(t *testing.T) {
	t.Parallel()

	t.Run("invalid capacity", func(t *testing.T) {
		_, err := New(filepath.Join(t.TempDir(), "test.mmap"), 0, 64)
		require.Error(t, err)
		require.Contains(t, err.Error(), "invalid capacity")

		_, err = New(filepath.Join(t.TempDir(), "test.mmap"), -1, 64)
		require.Error(t, err)
	})

	t.Run("invalid maxObjectSize", func(t *testing.T) {
		_, err := New(filepath.Join(t.TempDir(), "test.mmap"), 16, 0)
		require.Error(t, err)
		require.Contains(t, err.Error(), "invalid maxObjectSize")

		_, err = New(filepath.Join(t.TempDir(), "test.mmap"), 16, -1)
		require.Error(t, err)
	})
}

func TestNewProducerWithRegionValidation(t *testing.T) {
	t.Parallel()

	t.Run("nil region", func(t *testing.T) {
		_, err := NewWithRegion(nil, "test", 16, 64)
		require.Error(t, err)
		require.Contains(t, err.Error(), "nil region")
	})
}

func TestProducerBasicOps(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "producer.mmap")
	p, err := New(filename, 16, 64)
	require.NoError(t, err)
	defer func() { _ = p.Close(true) }()

	require.Equal(t, 16, p.Capacity())
	require.Equal(t, uint64(0), p.LastOfferedSequence())
}

func TestProducerNextToDispatch(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "producer.mmap")
	p, err := New(filename, 4, 64)
	require.NoError(t, err)
	defer func() { _ = p.Close(true) }()

	for i := 0; i < 4; i++ {
		addr, err := p.NextToDispatch()
		require.NoError(t, err)
		require.NotZero(t, addr)
		require.Equal(t, uint64(i+1), p.LastOfferedSequence())
	}

	_, err = p.NextToDispatch()
	require.ErrorIs(t, err, ring.ErrRingFull)
}

func TestProducerFlush(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "producer.mmap")
	region, err := memory.OpenOrCreateMmap(filename, ring.HeaderSize+4*64)
	require.NoError(t, err)

	p, err := NewWithRegion(region, filename, 4, 64)
	require.NoError(t, err)
	defer func() { _ = p.Close(true) }()

	base := uintptr(unsafe.Pointer(&region.Bytes()[0]))

	seq := ipcatomic.LoadVolatileLong(base + ring.ProducerSeqOffset)
	require.Equal(t, uint64(0), seq)

	_, _ = p.NextToDispatch()
	_, _ = p.NextToDispatch()

	seq = ipcatomic.LoadVolatileLong(base + ring.ProducerSeqOffset)
	require.Equal(t, uint64(0), seq)

	p.Flush()
	seq = ipcatomic.LoadVolatileLong(base + ring.ProducerSeqOffset)
	require.Equal(t, uint64(2), seq)
}

func TestProducerIndexCalc(t *testing.T) {
	t.Parallel()

	t.Run("power of two", func(t *testing.T) {
		filename := filepath.Join(t.TempDir(), "prod_pow2.mmap")
		p, err := New(filename, 8, 64)
		require.NoError(t, err)
		defer func() { _ = p.Close(true) }()

		require.True(t, p.isPowerOfTwo)

		require.Equal(t, uint64(0), p.calcIndex(1))
		require.Equal(t, uint64(1), p.calcIndex(2))
		require.Equal(t, uint64(7), p.calcIndex(8))
		require.Equal(t, uint64(0), p.calcIndex(9))
	})

	t.Run("non power of two", func(t *testing.T) {
		filename := filepath.Join(t.TempDir(), "prod_nonpow2.mmap")
		p, err := New(filename, 5, 64)
		require.NoError(t, err)
		defer func() { _ = p.Close(true) }()

		require.False(t, p.isPowerOfTwo)

		require.Equal(t, uint64(0), p.calcIndex(1))
		require.Equal(t, uint64(4), p.calcIndex(5))
		require.Equal(t, uint64(0), p.calcIndex(6))
	})
}

func TestProducerRingFullThenConsume(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "full.mmap")
	region, err := memory.OpenOrCreateMmap(filename, ring.HeaderSize+4*64)
	require.NoError(t, err)

	p, err := NewWithRegion(region, filename, 4, 64)
	require.NoError(t, err)
	defer func() { _ = p.Close(true) }()

	base := uintptr(unsafe.Pointer(&region.Bytes()[0]))

	for i := 0; i < 4; i++ {
		_, err := p.NextToDispatch()
		require.NoError(t, err)
	}
	p.Flush()

	_, err = p.NextToDispatch()
	require.ErrorIs(t, err, ring.ErrRingFull)

	ipcatomic.StoreVolatileLong(base+ring.ConsumerSeqOffset, 2)

	_, err = p.NextToDispatch()
	require.NoError(t, err)
	_, err = p.NextToDispatch()
	require.NoError(t, err)

	_, err = p.NextToDispatch()
	require.ErrorIs(t, err, ring.ErrRingFull)
}
