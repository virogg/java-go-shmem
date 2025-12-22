package main

import (
	"flag"
	"log"

	"github.com/viroge/go-shmem/pkg/config"
	"github.com/viroge/go-shmem/pkg/ring"
)

func main() {
	configPath := flag.String("config", "../shared/config.json", "path to config.json")
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("load config %s: %v", *configPath, err)
	}

	if err := ring.Reset(cfg.Rings.Go2Java); err != nil {
		log.Fatalf("resetRing(go2java): %v", err)
	}
	if err := ring.Reset(cfg.Rings.Java2Go); err != nil {
		log.Fatalf("resetRing(java2go): %v", err)
	}
}
