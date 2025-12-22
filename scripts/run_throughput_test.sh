#!/bin/bash
# Throughput measurement with batching
set -e
source "$(dirname "$0")/common.sh"

N=${1:-1000000}
BATCH=${2:-100}

check_java
check_go
build_java
cleanup_rings

log_info "=== Throughput Test: $N msgs, batch=$BATCH ==="

# Start Java
java $JAVA_OPTS -cp "$(get_java_cp)" com.jgshmem.example.JavaEchoService "$CONFIG_FILE" &
JAVA_PID=$!
trap "kill $JAVA_PID 2>/dev/null" EXIT
sleep 2

# Run throughput test (rings already reset)
(cd "$GO_DIR" && go run cmd/throughput/main.go -config "$CONFIG_FILE" -n "$N" -batch "$BATCH")

log_info "=== Throughput Test Complete ==="
