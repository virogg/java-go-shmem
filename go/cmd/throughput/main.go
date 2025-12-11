package main

import (
	"flag"
	"fmt"
	"log"
	"time"

	"github.com/viroge/go-shmem/pkg/config"
	"github.com/viroge/go-shmem/pkg/memory"
	"github.com/viroge/go-shmem/pkg/message"
	"github.com/viroge/go-shmem/pkg/ring"
)

func main() {
	configPath := flag.String("config", "../shared/config.json", "path to config.json")
	n := flag.Int("n", 1000000, "number of messages")
	batchSize := flag.Int("batch", 100, "flush batch size")
	reset := flag.Bool("reset", false, "reset rings")
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("load config: %v", err)
	}

	if *reset {
		resetRing(cfg.Rings.Go2Java)
		resetRing(cfg.Rings.Java2Go)
	}

	producer, err := openProducer(cfg.Rings.Go2Java)
	if err != nil {
		log.Fatalf("producer: %v", err)
	}
	defer producer.Close(false)

	consumer, err := openConsumer(cfg.Rings.Java2Go)
	if err != nil {
		log.Fatalf("consumer: %v", err)
	}
	defer consumer.Close(false)

	maxPayload := cfg.Rings.Go2Java.MaxObjectSize - 4
	reqMsg := message.NewPayloadMessage(maxPayload)
	respMsg := message.NewPayloadMessage(maxPayload)

	payload := []byte("throughput-test-payload-data")
	reqMsg.SetPayload(payload)

	fmt.Printf("Throughput test: %d msgs, batch=%d\n", *n, *batchSize)

	sent := 0
	received := 0
	pending := 0

	start := time.Now()

	for received < *n {
		// Send batch
		for pending < *batchSize && sent < *n {
			addr, err := producer.NextToDispatch()
			if err == ring.ErrRingFull {
				break
			}
			if err != nil {
				log.Fatalf("NextToDispatch: %v", err)
			}
			reqMsg.WriteTo(addr)
			sent++
			pending++
		}
		if pending > 0 {
			producer.Flush()
		}

		// Receive available
		for consumer.AvailableToFetch() > 0 {
			addr, _ := consumer.FetchNext()
			respMsg.ReadFrom(addr)
			consumer.DoneFetching()
			received++
			pending--
		}
	}

	elapsed := time.Since(start)
	throughput := float64(*n) / elapsed.Seconds()

	fmt.Printf("\n=== Throughput Results ===\n")
	fmt.Printf("Messages:   %d\n", *n)
	fmt.Printf("Time:       %v\n", elapsed)
	fmt.Printf("Throughput: %.0f msg/s\n", throughput)
	fmt.Printf("Latency:    %.2f µs/msg\n", float64(elapsed.Microseconds())/float64(*n))
}

func openProducer(r config.Ring) (*ring.Producer, error) {
	backend := r.Backend
	if backend == "" {
		backend = memory.BackendMmap
	}
	name := r.Filename
	if backend == memory.BackendPosixShm {
		name = r.ShmName
	}
	totalSize := ring.HeaderSize + r.Capacity*r.MaxObjectSize
	region, err := memory.OpenRegion(backend, name, totalSize)
	if err != nil {
		return nil, err
	}
	return ring.NewProducerWithRegion(region, name, r.Capacity, r.MaxObjectSize)
}

func openConsumer(r config.Ring) (*ring.Consumer, error) {
	backend := r.Backend
	if backend == "" {
		backend = memory.BackendMmap
	}
	name := r.Filename
	if backend == memory.BackendPosixShm {
		name = r.ShmName
	}
	totalSize := ring.HeaderSize + r.Capacity*r.MaxObjectSize
	region, err := memory.OpenRegion(backend, name, totalSize)
	if err != nil {
		return nil, err
	}
	return ring.NewConsumerWithRegion(region, name, r.Capacity, r.MaxObjectSize)
}

func resetRing(r config.Ring) {
	backend := r.Backend
	if backend == "" {
		backend = memory.BackendMmap
	}
	name := r.Filename
	if backend == memory.BackendPosixShm {
		name = r.ShmName
	}
	memory.DeleteRegion(backend, name)
}
