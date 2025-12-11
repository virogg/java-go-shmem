package main

import (
	"flag"
	"fmt"
	"log"

	"github.com/viroge/go-shmem/pkg/config"
	"github.com/viroge/go-shmem/pkg/memory"
)

func main() {
	configPath := flag.String("config", "../shared/config.json", "path to config.json")
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("load config %s: %v", *configPath, err)
	}

	if err := resetRing(cfg.Rings.Go2Java); err != nil {
		log.Fatalf("resetRing(go2java): %v", err)
	}
	if err := resetRing(cfg.Rings.Java2Go); err != nil {
		log.Fatalf("resetRing(java2go): %v", err)
	}
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
