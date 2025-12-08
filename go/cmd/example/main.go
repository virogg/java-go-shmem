package main

import (
	"flag"
	"fmt"
	"log"

	"github.com/viroge/go-shmem/pkg/config"
	"github.com/viroge/go-shmem/pkg/memory"
	"github.com/viroge/go-shmem/pkg/message"
	"github.com/viroge/go-shmem/pkg/ring"
)

func main() {
	configPath := flag.String("config", "../shared/config.json", "path to config.json")
	n := flag.Int("n", 100000, "number of request/response round-trips")
	reset := flag.Bool("reset", false, "reset ring state before run (must be done before starting Java/Go processes)")
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("load config %s: %v", *configPath, err)
	}

	if *reset {
		if err := resetRing(cfg.Rings.Go2Java); err != nil {
			log.Fatalf("resetRing(go2java): %v", err)
		}
		if err := resetRing(cfg.Rings.Java2Go); err != nil {
			log.Fatalf("resetRing(java2go): %v", err)
		}
	}

	go2java, err := openProducer(cfg.Rings.Go2Java)
	if err != nil {
		log.Fatalf("NewProducer(go2java): %v", err)
	}
	defer func() { _ = go2java.Close(false) }()

	java2go, err := openConsumer(cfg.Rings.Java2Go)
	if err != nil {
		log.Fatalf("NewConsumer(java2go): %v", err)
	}
	defer func() { _ = java2go.Close(false) }()

	maxPayloadSize := cfg.Rings.Go2Java.MaxObjectSize - 4

	reqMsg := message.NewPayloadMessage(maxPayloadSize)
	respMsg := message.NewPayloadMessage(maxPayloadSize)

	for i := 1; i <= *n; i++ {
		for {
			addr, err := go2java.NextToDispatch()
			if err == ring.ErrRingFull {
				continue
			}
			if err != nil {
				log.Fatalf("NextToDispatch: %v", err)
			}

			// Create a simple payload containing the iteration number as string
			payload := fmt.Sprintf("request-%d", i)
			if err := reqMsg.SetPayload([]byte(payload)); err != nil {
				log.Fatalf("SetPayload: %v", err)
			}

			reqMsg.WriteTo(addr)
			go2java.Flush()
			break
		}

		for java2go.AvailableToFetch() == 0 {
			// busy spin
		}

		addr, err := java2go.FetchNext()
		if err != nil {
			log.Fatalf("FetchNext: %v", err)
		}

		if err := respMsg.ReadFrom(addr); err != nil {
			log.Fatalf("ReadFrom: %v", err)
		}
		java2go.DoneFetching()

		receivedPayload := string(respMsg.GetPayload())
		expectedPayload := fmt.Sprintf("request-%d", i)
		if receivedPayload != expectedPayload {
			log.Fatalf("mismatch at %d: got=%q, expected=%q", i, receivedPayload, expectedPayload)
		}

		if i%10000 == 0 {
			fmt.Printf("ok %d/%d\n", i, *n)
		}
	}

	fmt.Println("SUCCESS")
}

func openProducer(r config.Ring) (*ring.Producer, error) {
	backend := r.Backend
	if backend == "" {
		backend = memory.BackendMmap
	}
	name, err := ringName(r)
	if err != nil {
		return nil, err
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
	name, err := ringName(r)
	if err != nil {
		return nil, err
	}

	totalSize := ring.HeaderSize + r.Capacity*r.MaxObjectSize
	region, err := memory.OpenRegion(backend, name, totalSize)
	if err != nil {
		return nil, err
	}

	return ring.NewConsumerWithRegion(region, name, r.Capacity, r.MaxObjectSize)
}

func resetRing(r config.Ring) error {
	backend := r.Backend
	if backend == "" {
		backend = memory.BackendMmap
	}
	name, err := ringName(r)
	if err != nil {
		return err
	}
	return memory.DeleteRegion(backend, name)
}

func ringName(r config.Ring) (string, error) {
	backend := r.Backend
	if backend == "" {
		backend = memory.BackendMmap
	}
	switch backend {
	case memory.BackendMmap:
		return r.Filename, nil
	case memory.BackendPosixShm:
		return r.ShmName, nil
	default:
		return "", fmt.Errorf("unknown backend: %q", backend)
	}
}
