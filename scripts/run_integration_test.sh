#!/bin/bash
# Full integration test: start Java echo, run Go client, verify success
set -e
source "$(dirname "$0")/common.sh"

N=${1:-10000}
TIMEOUT=${2:-60}

check_java
check_go

log_info "=== Integration Test: $N messages ==="

# Build Java
build_java

# Clean up rings
cleanup_rings

# Start Java echo service in background
log_info "Starting Java echo service..."
java $JAVA_OPTS -cp "$(get_java_cp)" com.jgshmem.example.JavaEchoService "$CONFIG_FILE" &
JAVA_PID=$!

cleanup() {
    log_info "Cleaning up..."
    kill $JAVA_PID 2>/dev/null || true
    wait $JAVA_PID 2>/dev/null || true
}
trap cleanup EXIT

# Wait for Java to initialize
sleep 2

log_info "Running Go client..."
(cd "$GO_DIR" && go run cmd/example/main.go -config "$CONFIG_FILE" -n "$N")
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    log_info "=== PASS: Integration test completed ==="
else
    log_error "=== FAIL: Integration test failed ==="
    exit 1
fi
