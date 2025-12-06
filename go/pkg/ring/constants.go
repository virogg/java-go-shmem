package ring

const (
	CacheLine = 64

	SeqPrefixPadding = 24

	HeaderSize = CacheLine + CacheLine

	ProducerSeqOffset = SeqPrefixPadding
	ConsumerSeqOffset = CacheLine + SeqPrefixPadding
)

