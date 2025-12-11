package main

import (
	"flag"
	"fmt"
	"log"
	"sort"
	"time"

	"github.com/viroge/go-shmem/pkg/config"
	"github.com/viroge/go-shmem/pkg/memory"
	"github.com/viroge/go-shmem/pkg/message"
	"github.com/viroge/go-shmem/pkg/ring"
)

func main() {
	configPath := flag.String("config", "../shared/config.json", "path to config.json")
	n := flag.Int("n", 10000, "number of round-trips")
	warmup := flag.Int("warmup", 1000, "warmup iterations")
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

	// Warmup
	fmt.Printf("Warmup: %d iterations\n", *warmup)
	for i := 0; i < *warmup; i++ {
		roundTrip(producer, consumer, reqMsg, respMsg, i)
	}

	// Measure
	fmt.Printf("Measuring: %d iterations\n", *n)
	latencies := make([]time.Duration, *n)

	for i := 0; i < *n; i++ {
		start := time.Now()
		roundTrip(producer, consumer, reqMsg, respMsg, i)
		latencies[i] = time.Since(start)
	}

	// Stats
	sort.Slice(latencies, func(i, j int) bool { return latencies[i] < latencies[j] })

	var total time.Duration
	for _, l := range latencies {
		total += l
	}

	fmt.Printf("\n=== Latency Results (%d samples) ===\n", *n)
	fmt.Printf("Min:    %v\n", latencies[0])
	fmt.Printf("Max:    %v\n", latencies[len(latencies)-1])
	fmt.Printf("Avg:    %v\n", total/time.Duration(*n))
	fmt.Printf("P50:    %v\n", latencies[*n*50/100])
	fmt.Printf("P90:    %v\n", latencies[*n*90/100])
	fmt.Printf("P99:    %v\n", latencies[*n*99/100])
	fmt.Printf("P99.9:  %v\n", latencies[*n*999/1000])
}

func roundTrip(p *ring.Producer, c *ring.Consumer, req, resp *message.PayloadMessage, seq int) {
	for {
		addr, err := p.NextToDispatch()
		if err == ring.ErrRingFull {
			continue
		}
		if err != nil {
			log.Fatalf("NextToDispatch: %v", err)
		}
		req.SetPayload([]byte(fmt.Sprintf("req-%d", seq)))
		req.WriteTo(addr)
		p.Flush()
		break
	}

	for c.AvailableToFetch() == 0 {
	}

	addr, _ := c.FetchNext()
	resp.ReadFrom(addr)
	c.DoneFetching()
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
