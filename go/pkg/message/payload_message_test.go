package message

import (
	"path/filepath"
	"testing"
	"unsafe"

	"github.com/viroge/go-shmem/pkg/memory"

	"github.com/stretchr/testify/require"
)

func TestNewPayloadMessageValidation(t *testing.T) {
	t.Parallel()

	require.Panics(t, func() { NewPayloadMessage(0) })
	require.Panics(t, func() { NewPayloadMessage(-1) })
}

func TestPayloadMessageBasic(t *testing.T) {
	t.Parallel()

	msg := NewPayloadMessage(64)

	require.Equal(t, 68, msg.GetMaxSize()) // 4 + 64
	require.Equal(t, 64, msg.GetMaxPayloadSize())
	require.Nil(t, msg.GetPayload())

	err := msg.SetPayload([]byte("hello"))
	require.NoError(t, err)
	require.Equal(t, []byte("hello"), msg.GetPayload())

	msg.Clear()
	require.Nil(t, msg.GetPayload())
}

func TestPayloadMessageSetPayloadOversize(t *testing.T) {
	t.Parallel()

	msg := NewPayloadMessage(8)

	err := msg.SetPayload([]byte("this is too long"))
	require.Error(t, err)
	require.Contains(t, err.Error(), "exceeds max")
}

func TestPayloadMessageWriteReadRoundtrip(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "msg.mmap")
	region, err := memory.OpenOrCreateMmap(filename, 1024)
	require.NoError(t, err)
	defer func() { _ = region.Close() }()

	base := uintptr(unsafe.Pointer(&region.Bytes()[0]))
	addr := base + 128

	// write
	msg1 := NewPayloadMessage(256)
	_ = msg1.SetPayload([]byte("test payload data"))
	msg1.WriteTo(addr)

	// read
	msg2 := NewPayloadMessage(256)
	err = msg2.ReadFrom(addr)
	require.NoError(t, err)
	require.Equal(t, msg1.GetPayload(), msg2.GetPayload())
}

func TestPayloadMessageEmptyPayload(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "empty.mmap")
	region, err := memory.OpenOrCreateMmap(filename, 256)
	require.NoError(t, err)
	defer func() { _ = region.Close() }()

	base := uintptr(unsafe.Pointer(&region.Bytes()[0]))

	msg1 := NewPayloadMessage(64)
	msg1.PayloadSize = 0
	msg1.WriteTo(base)

	msg2 := NewPayloadMessage(64)
	err = msg2.ReadFrom(base)
	require.NoError(t, err)
	require.Nil(t, msg2.GetPayload())
}

func TestPayloadMessageMaxPayload(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "max.mmap")
	region, err := memory.OpenOrCreateMmap(filename, 1024)
	require.NoError(t, err)
	defer func() { _ = region.Close() }()

	base := uintptr(unsafe.Pointer(&region.Bytes()[0]))

	maxSize := 128
	payload := make([]byte, maxSize)
	for i := range payload {
		payload[i] = byte(i % 256)
	}

	msg1 := NewPayloadMessage(maxSize)
	_ = msg1.SetPayload(payload)
	msg1.WriteTo(base)

	msg2 := NewPayloadMessage(maxSize)
	err = msg2.ReadFrom(base)
	require.NoError(t, err)
	require.Equal(t, payload, msg2.GetPayload())
}

func TestPayloadMessageWritePanicsOnInvalidSize(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "panic.mmap")
	region, err := memory.OpenOrCreateMmap(filename, 256)
	require.NoError(t, err)
	defer func() { _ = region.Close() }()

	base := uintptr(unsafe.Pointer(&region.Bytes()[0]))

	msg := NewPayloadMessage(64)
	msg.PayloadSize = -1
	require.Panics(t, func() { msg.WriteTo(base) })

	msg.PayloadSize = 100 // > max
	require.Panics(t, func() { msg.WriteTo(base) })
}

func TestPayloadMessageReadInvalidSize(t *testing.T) {
	t.Parallel()

	filename := filepath.Join(t.TempDir(), "invalid.mmap")
	region, err := memory.OpenOrCreateMmap(filename, 256)
	require.NoError(t, err)
	defer func() { _ = region.Close() }()

	base := uintptr(unsafe.Pointer(&region.Bytes()[0]))

	sizeBytes := (*[4]byte)(unsafe.Pointer(base))
	sizeBytes[0] = 0xFF
	sizeBytes[1] = 0xFF
	sizeBytes[2] = 0xFF
	sizeBytes[3] = 0x7F

	msg := NewPayloadMessage(64)
	err = msg.ReadFrom(base)
	require.Error(t, err)
	require.Contains(t, err.Error(), "max")
}

func TestPayloadMessageString(t *testing.T) {
	t.Parallel()

	msg := NewPayloadMessage(64)
	_ = msg.SetPayload([]byte("test"))

	s := msg.String()
	require.Contains(t, s, "PayloadMessage")
	require.Contains(t, s, "4")
}
