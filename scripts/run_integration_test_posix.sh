#!/bin/bash
# Integration test using POSIX shared memory
# NOTE: POSIX shm requires clean environment - no stale segments
set -e
source "$(dirname "$0")/common.sh"

N=${1:-10000}

check_java
check_go

log_info "=== Integration Test (POSIX shm): $N messages ==="

# Build
build_java
build_native

setup_cgo_env
cleanup_posix_shm

# Java needs native lib path
NATIVE_LIB=$(get_native_lib_path)
export JAVA_OPTS="$JAVA_OPTS -Djava.library.path=$(dirname $NATIVE_LIB)"

# Go must create shm segments first
log_info "Initializing POSIX shm segments..."
(cd "$GO_DIR" && go run -tags posixshm cmd/init/main.go -config "$CONFIG_POSIX")

log_info "Starting Java echo service..."
java $JAVA_OPTS -cp "$(get_java_cp)" com.jgshmem.example.JavaEchoService "$CONFIG_POSIX" &
JAVA_PID=$!

cleanup() {
    log_info "Cleaning up..."
    kill $JAVA_PID 2>/dev/null || true
    wait $JAVA_PID 2>/dev/null || true
}
trap cleanup EXIT

sleep 2

log_info "Running Go client..."
(cd "$GO_DIR" && go run -tags posixshm cmd/example/main.go -config "$CONFIG_POSIX" -n "$N")
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    log_info "=== PASS: Integration test (POSIX shm) completed ==="
else
    log_error "=== FAIL: Integration test (POSIX shm) failed ==="
    exit 1
fi
