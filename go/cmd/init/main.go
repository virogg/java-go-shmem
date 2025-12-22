package main

import (
	"flag"
	"log"

	"github.com/viroge/go-shmem/pkg/config"
	"github.com/viroge/go-shmem/pkg/ring"
)

// Initializes ring buffer memory regions (creates them if they don't exist)
func main() {
	configPath := flag.String("config", "../shared/config.json", "path to config.json")
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("load config: %v", err)
	}

	if err := ring.Init(cfg.Rings.Go2Java); err != nil {
		log.Fatalf("initRing(go2java): %v", err)
	}
	if err := ring.Init(cfg.Rings.Java2Go); err != nil {
		log.Fatalf("initRing(java2go): %v", err)
	}

	log.Println("Rings initialized")
}
