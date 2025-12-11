#!/bin/bash
# Latency test using POSIX shared memory
set -e
source "$(dirname "$0")/common.sh"

N=${1:-10000}
WARMUP=${2:-1000}

check_java
check_go
build_java
build_native
setup_cgo_env
cleanup_posix_shm

log_info "=== Latency Test (POSIX shm): $N samples, $WARMUP warmup ==="

NATIVE_LIB=$(get_native_lib_path)
export JAVA_OPTS="$JAVA_OPTS -Djava.library.path=$(dirname $NATIVE_LIB)"

log_info "Initializing POSIX shm segments..."
(cd "$GO_DIR" && go run -tags posixshm cmd/init/main.go -config "$CONFIG_POSIX")

java $JAVA_OPTS -cp "$(get_java_cp)" com.jgshmem.example.JavaEchoService "$CONFIG_POSIX" &
JAVA_PID=$!
trap "kill $JAVA_PID 2>/dev/null" EXIT
sleep 2

(cd "$GO_DIR" && go run -tags posixshm cmd/latency/main.go -config "$CONFIG_POSIX" -n "$N" -warmup "$WARMUP")

log_info "=== Latency Test (POSIX shm) Complete ==="
