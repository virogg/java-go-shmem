#!/bin/bash
# Latency measurement test
set -e
source "$(dirname "$0")/common.sh"

N=${1:-10000}
WARMUP=${2:-1000}

check_java
check_go
build_java
cleanup_rings

log_info "=== Latency Test: $N samples, $WARMUP warmup ==="

# Start Java
java $JAVA_OPTS -cp "$(get_java_cp)" com.jgshmem.example.JavaEchoService "$CONFIG_FILE" &
JAVA_PID=$!
trap "kill $JAVA_PID 2>/dev/null" EXIT
sleep 2

# Run latency test (rings already reset)
(cd "$GO_DIR" && go run cmd/latency/main.go -config "$CONFIG_FILE" -n "$N" -warmup "$WARMUP")

log_info "=== Latency Test Complete ==="
