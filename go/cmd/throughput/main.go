package main

import (
	"flag"
	"fmt"
	"log"
	"time"

	"github.com/viroge/go-shmem/pkg/config"
	"github.com/viroge/go-shmem/pkg/message"
	"github.com/viroge/go-shmem/pkg/ring"
	"github.com/viroge/go-shmem/pkg/ring/consumer"
	"github.com/viroge/go-shmem/pkg/ring/producer"
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
		ring.Reset(cfg.Rings.Go2Java)
		ring.Reset(cfg.Rings.Java2Go)
	}

	producer, err := producer.Open(cfg.Rings.Go2Java)
	if err != nil {
		log.Fatalf("producer: %v", err)
	}
	defer producer.Close(false)

	consumer, err := consumer.Open(cfg.Rings.Java2Go)
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