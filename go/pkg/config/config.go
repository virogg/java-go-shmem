package config

import (
	"encoding/json"
	"fmt"
	"os"
)

type Ring struct {
	Filename      string `json:"filename"`
	Backend       string `json:"backend,omitempty"`
	ShmName       string `json:"shmName,omitempty"`
	Capacity      int    `json:"capacity"`
	MaxObjectSize int    `json:"maxObjectSize"`
	TimeoutMs     int    `json:"timeoutMs,omitempty"`
}

type Config struct {
	Rings struct {
		Go2Java Ring `json:"go2java"`
		Java2Go Ring `json:"java2go"`
	} `json:"rings"`
}

func Load(path string) (*Config, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	var cfg Config
	if err := json.Unmarshal(b, &cfg); err != nil {
		return nil, err
	}

	if err := validateRing("go2java", cfg.Rings.Go2Java); err != nil {
		return nil, err
	}
	if err := validateRing("java2go", cfg.Rings.Java2Go); err != nil {
		return nil, err
	}
	return &cfg, nil
}

func validateRing(label string, r Ring) error {
	if r.Capacity <= 0 {
		return fmt.Errorf("invalid config: %s.capacity must be > 0 (got %d)", label, r.Capacity)
	}
	if r.MaxObjectSize <= 0 {
		return fmt.Errorf("invalid config: %s.maxObjectSize must be > 0 (got %d)", label, r.MaxObjectSize)
	}
	if r.TimeoutMs < 0 {
		return fmt.Errorf("invalid config: %s.timeoutMs must be >= 0 (got %d)", label, r.TimeoutMs)
	}

	backend := r.Backend
	if backend == "" {
		backend = "mmap"
	}

	switch backend {
	case "mmap":
		if r.Filename == "" {
			return fmt.Errorf("invalid config: %s.filename required for mmap backend", label)
		}
	case "posix_shm":
		if r.ShmName == "" {
			return fmt.Errorf("invalid config: %s.shmName required for posix_shm backend", label)
		}
		if r.ShmName[0] != '/' {
			return fmt.Errorf("invalid config: %s.shmName must start with '/' (got %q)", label, r.ShmName)
		}
	default:
		return fmt.Errorf("invalid config: %s.backend unknown: %q", label, backend)
	}

	return nil
}

func (r *Ring) GetTimeout() int {
	if r.TimeoutMs <= 0 {
		return 5000
	}
	return r.TimeoutMs
}
