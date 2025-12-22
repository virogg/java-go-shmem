package config

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/require"
)

func writeConfig(t *testing.T, content string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "config.json")
	err := os.WriteFile(path, []byte(content), 0644)
	require.NoError(t, err)
	return path
}

func TestLoadValidConfig(t *testing.T) {
	t.Parallel()

	content := `{
		"rings": {
			"go2java": {
				"filename": "/tmp/go2java.mmap",
				"capacity": 1024,
				"maxObjectSize": 1028
			},
			"java2go": {
				"filename": "/tmp/java2go.mmap",
				"capacity": 512,
				"maxObjectSize": 256,
				"timeoutMs": 10000
			}
		}
	}`

	cfg, err := Load(writeConfig(t, content))
	require.NoError(t, err)
	require.NotNil(t, cfg)

	require.Equal(t, "/tmp/go2java.mmap", cfg.Rings.Go2Java.Filename)
	require.Equal(t, 1024, cfg.Rings.Go2Java.Capacity)
	require.Equal(t, 1028, cfg.Rings.Go2Java.MaxObjectSize)
	require.Equal(t, 5000, cfg.Rings.Go2Java.GetTimeout()) // default

	require.Equal(t, "/tmp/java2go.mmap", cfg.Rings.Java2Go.Filename)
	require.Equal(t, 512, cfg.Rings.Java2Go.Capacity)
	require.Equal(t, 10000, cfg.Rings.Java2Go.GetTimeout())
}

func TestLoadPosixShmConfig(t *testing.T) {
	t.Parallel()

	content := `{
		"rings": {
			"go2java": {
				"backend": "posix_shm",
				"shmName": "/go2java",
				"capacity": 1024,
				"maxObjectSize": 256
			},
			"java2go": {
				"backend": "posix_shm",
				"shmName": "/java2go",
				"capacity": 512,
				"maxObjectSize": 128
			}
		}
	}`

	cfg, err := Load(writeConfig(t, content))
	require.NoError(t, err)
	require.Equal(t, "posix_shm", cfg.Rings.Go2Java.Backend)
	require.Equal(t, "/go2java", cfg.Rings.Go2Java.ShmName)
}

func TestLoadInvalidCapacity(t *testing.T) {
	t.Parallel()

	content := `{
		"rings": {
			"go2java": {
				"filename": "/tmp/test.mmap",
				"capacity": 0,
				"maxObjectSize": 64
			},
			"java2go": {
				"filename": "/tmp/test2.mmap",
				"capacity": 1,
				"maxObjectSize": 64
			}
		}
	}`

	_, err := Load(writeConfig(t, content))
	require.Error(t, err)
	require.Contains(t, err.Error(), "capacity")
}

func TestLoadInvalidMaxObjectSize(t *testing.T) {
	t.Parallel()

	content := `{
		"rings": {
			"go2java": {
				"filename": "/tmp/test.mmap",
				"capacity": 16,
				"maxObjectSize": -1
			},
			"java2go": {
				"filename": "/tmp/test2.mmap",
				"capacity": 16,
				"maxObjectSize": 64
			}
		}
	}`

	_, err := Load(writeConfig(t, content))
	require.Error(t, err)
	require.Contains(t, err.Error(), "maxObjectSize")
}

func TestLoadMissingFilename(t *testing.T) {
	t.Parallel()

	content := `{
		"rings": {
			"go2java": {
				"capacity": 16,
				"maxObjectSize": 64
			},
			"java2go": {
				"filename": "/tmp/test.mmap",
				"capacity": 16,
				"maxObjectSize": 64
			}
		}
	}`

	_, err := Load(writeConfig(t, content))
	require.Error(t, err)
	require.Contains(t, err.Error(), "filename required")
}

func TestLoadPosixShmMissingShmName(t *testing.T) {
	t.Parallel()

	content := `{
		"rings": {
			"go2java": {
				"backend": "posix_shm",
				"capacity": 16,
				"maxObjectSize": 64
			},
			"java2go": {
				"filename": "/tmp/test.mmap",
				"capacity": 16,
				"maxObjectSize": 64
			}
		}
	}`

	_, err := Load(writeConfig(t, content))
	require.Error(t, err)
	require.Contains(t, err.Error(), "shmName required")
}

func TestLoadPosixShmInvalidShmName(t *testing.T) {
	t.Parallel()

	content := `{
		"rings": {
			"go2java": {
				"backend": "posix_shm",
				"shmName": "noslash",
				"capacity": 16,
				"maxObjectSize": 64
			},
			"java2go": {
				"filename": "/tmp/test.mmap",
				"capacity": 16,
				"maxObjectSize": 64
			}
		}
	}`

	_, err := Load(writeConfig(t, content))
	require.Error(t, err)
	require.Contains(t, err.Error(), "must start with '/'")
}

func TestLoadUnknownBackend(t *testing.T) {
	t.Parallel()

	content := `{
		"rings": {
			"go2java": {
				"backend": "invalid",
				"filename": "/tmp/test.mmap",
				"capacity": 16,
				"maxObjectSize": 64
			},
			"java2go": {
				"filename": "/tmp/test.mmap",
				"capacity": 16,
				"maxObjectSize": 64
			}
		}
	}`

	_, err := Load(writeConfig(t, content))
	require.Error(t, err)
	require.Contains(t, err.Error(), "unknown")
}

func TestLoadInvalidTimeout(t *testing.T) {
	t.Parallel()

	content := `{
		"rings": {
			"go2java": {
				"filename": "/tmp/test.mmap",
				"capacity": 16,
				"maxObjectSize": 64,
				"timeoutMs": -5
			},
			"java2go": {
				"filename": "/tmp/test.mmap",
				"capacity": 16,
				"maxObjectSize": 64
			}
		}
	}`

	_, err := Load(writeConfig(t, content))
	require.Error(t, err)
	require.Contains(t, err.Error(), "timeoutMs")
}

func TestLoadFileNotFound(t *testing.T) {
	t.Parallel()

	_, err := Load("/nonexistent/path/config.json")
	require.Error(t, err)
}

func TestLoadInvalidJSON(t *testing.T) {
	t.Parallel()

	content := `{ not valid json `
	_, err := Load(writeConfig(t, content))
	require.Error(t, err)
}

func TestGetTimeoutDefault(t *testing.T) {
	t.Parallel()

	r := Ring{TimeoutMs: 0}
	require.Equal(t, 5000, r.GetTimeout())

	r.TimeoutMs = -1
	require.Equal(t, 5000, r.GetTimeout())

	r.TimeoutMs = 1000
	require.Equal(t, 1000, r.GetTimeout())
}
