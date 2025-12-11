#!/bin/bash
# Compare mmap vs POSIX shm backends
set -e
source "$(dirname "$0")/common.sh"

N=${1:-10000}
WARMUP=${2:-1000}
BATCH=${3:-100}

log_info "=========================================="
log_info "   Backend Comparison: mmap vs POSIX shm"
log_info "=========================================="

log_info "\n--- mmap Backend ---"
log_info "[mmap] Latency test..."
"$SCRIPT_DIR/run_latency_test.sh" "$N" "$WARMUP" 2>&1 | grep -E "(Min|Max|Avg|P50|P90|P99):"

log_info "[mmap] Throughput test..."
"$SCRIPT_DIR/run_throughput_test.sh" "$N" "$BATCH" 2>&1 | grep -E "(Throughput|Latency):"

log_info "\n--- POSIX shm Backend ---"
log_info "[posix] Latency test..."
"$SCRIPT_DIR/run_latency_test_posix.sh" "$N" "$WARMUP" 2>&1 | grep -E "(Min|Max|Avg|P50|P90|P99):"

log_info "[posix] Throughput test..."
"$SCRIPT_DIR/run_throughput_test_posix.sh" "$N" "$BATCH" 2>&1 | grep -E "(Throughput|Latency):"

log_info "\n=========================================="
log_info "   Comparison Complete"
log_info "=========================================="
