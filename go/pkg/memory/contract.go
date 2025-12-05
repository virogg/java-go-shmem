package memory

type Region interface {
	Bytes() []byte
	Size() int
	Close() error
	Delete() error
}