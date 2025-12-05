package message

import (
	"encoding/binary"
	"fmt"
	"unsafe"
)

type PayloadMessage struct {
	PayloadSize int32
	Payload     []byte
	maxSize     int
}

func NewPayloadMessage(maxPayloadSize int) *PayloadMessage {
	if maxPayloadSize <= 0 {
		panic(fmt.Sprintf("maxPayloadSize must be > 0, got: %d", maxPayloadSize))
	}
	return &PayloadMessage{
		Payload:     make([]byte, maxPayloadSize),
		maxSize:     maxPayloadSize,
		PayloadSize: 0,
	}
}

func (m *PayloadMessage) GetMaxSize() int {
	return 4 + m.maxSize
}

func (m *PayloadMessage) GetMaxPayloadSize() int {
	return m.maxSize
}

func (m *PayloadMessage) Clear() {
	m.PayloadSize = 0
}

func (m *PayloadMessage) WriteTo(addr uintptr) {
	if m.PayloadSize < 0 || m.PayloadSize > int32(m.maxSize) {
		panic(fmt.Sprintf("invalid PayloadSize: %d (max: %d)", m.PayloadSize, m.maxSize))
	}

	sizeBytes := (*[4]byte)(unsafe.Pointer(addr))
	binary.LittleEndian.PutUint32(sizeBytes[:], uint32(m.PayloadSize))

	if m.PayloadSize > 0 {
		payloadAddr := addr + 4
		payloadSlice := (*[1 << 30]byte)(unsafe.Pointer(payloadAddr))[:m.PayloadSize:m.PayloadSize]
		copy(payloadSlice, m.Payload[:m.PayloadSize])
	}
}

func (m *PayloadMessage) ReadFrom(addr uintptr) error {
	sizeBytes := (*[4]byte)(unsafe.Pointer(addr))
	m.PayloadSize = int32(binary.LittleEndian.Uint32(sizeBytes[:]))

	if m.PayloadSize < 0 {
		return fmt.Errorf("invalid PayloadSize read from memory: %d (negative)", m.PayloadSize)
	}
	if m.PayloadSize > int32(m.maxSize) {
		return fmt.Errorf("invalid PayloadSize read from memory: %d (max: %d)", m.PayloadSize, m.maxSize)
	}

	if m.PayloadSize > 0 {
		payloadAddr := addr + 4
		payloadSlice := (*[1 << 30]byte)(unsafe.Pointer(payloadAddr))[:m.PayloadSize:m.PayloadSize]
		copy(m.Payload[:m.PayloadSize], payloadSlice)
	}

	return nil
}

func (m *PayloadMessage) SetPayload(data []byte) error {
	if len(data) > m.maxSize {
		return fmt.Errorf("payload size %d exceeds max %d", len(data), m.maxSize)
	}
	copy(m.Payload, data)
	m.PayloadSize = int32(len(data))
	return nil
}

func (m *PayloadMessage) GetPayload() []byte {
	if m.PayloadSize <= 0 {
		return nil
	}
	return m.Payload[:m.PayloadSize]
}

func (m *PayloadMessage) String() string {
	return fmt.Sprintf("PayloadMessage{payloadSize=%d, maxSize=%d}", m.PayloadSize, m.maxSize)
}
