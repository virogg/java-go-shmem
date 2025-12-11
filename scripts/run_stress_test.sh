#!/bin/bash
# Stress test with high message counts
set -e
source "$(dirname "$0")/common.sh"

# Test parameters
ITERATIONS=${1:-3}
MESSAGES=(10000 100000 500000 1000000)

check_java
check_go
build_java

log_info "=== Stress Test: $ITERATIONS iterations ==="

for msg_count in "${MESSAGES[@]}"; do
    log_info "--- Testing $msg_count messages ---"

    for ((i=1; i<=ITERATIONS; i++)); do
        cleanup_rings

        # Start Java
        java $JAVA_OPTS -cp "$(get_java_cp)" com.jgshmem.example.JavaEchoService "$CONFIG_FILE" &
        JAVA_PID=$!
        sleep 2

        # Run Go and capture timing
        START=$(date +%s.%N)
        (cd "$GO_DIR" && go run cmd/example/main.go -config "$CONFIG_FILE" -n "$msg_count" -reset) || {
            log_error "FAIL at $msg_count msgs, iter $i"
            kill $JAVA_PID 2>/dev/null
            exit 1
        }
        END=$(date +%s.%N)

        kill $JAVA_PID 2>/dev/null
        wait $JAVA_PID 2>/dev/null || true

        DURATION=$(echo "$END - $START" | bc)
        THROUGHPUT=$(echo "scale=0; $msg_count / $DURATION" | bc)
        log_info "Iter $i: ${DURATION}s, ${THROUGHPUT} msg/s"
    done
done

log_info "=== Stress Test PASSED ==="
