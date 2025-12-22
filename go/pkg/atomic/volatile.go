package atomic

import (
	"fmt"
	"sync/atomic"
	"unsafe"
)

func LoadVolatileLong(addr uintptr) uint64 {
	if addr%8 != 0 {
		panic(fmt.Sprintf("unaligned uint64 address: 0x%x", addr))
	}
	return atomic.LoadUint64((*uint64)(unsafe.Pointer(addr)))
}

func StoreVolatileLong(addr uintptr, val uint64) {
	if addr%8 != 0 {
		panic(fmt.Sprintf("unaligned uint64 address: 0x%x", addr))
	}
	atomic.StoreUint64((*uint64)(unsafe.Pointer(addr)), val)
}

