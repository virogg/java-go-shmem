package atomic

import (
	"path/filepath"
	"testing"
	"unsafe"

	"github.com/viroge/go-shmem/pkg/memory"

	"github.com/stretchr/testify/require"
)

func TestVolatileRoundtrip(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "atomic.mmap")
	m, err := memory.OpenOrCreateMmap(filename, 4096)
	require.NoError(t, err)
	defer func() { _ = m.Close() }()

	base := uintptr(unsafe.Pointer(&m.Bytes()[0]))
	addr := base + 24

	StoreVolatileLong(addr, 123)
	got := LoadVolatileLong(addr)
	require.Equal(t, uint64(123), got)
}

func TestVolatileUnalignedPanics(t *testing.T) {
	require.Panics(t, func(){ StoreVolatileLong(uintptr(1), 1) })
}

