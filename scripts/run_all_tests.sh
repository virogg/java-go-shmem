#!/bin/bash
set -e
source "$(dirname "$0")/common.sh"


log_info "\n[1/5] Go unit tests"
(cd "$GO_DIR" && go test ./... -v)

log_info "\n[2/5] Java unit tests"
(cd "$JAVA_DIR" && mvn -q test)

log_info "\n[3/5] Basic integration test (1000 msgs)"
"$SCRIPT_DIR/run_integration_test.sh" 1000

log_info "\n[4/5] Latency test (5000 samples)"
"$SCRIPT_DIR/run_latency_test.sh" 5000 500

log_info "\n[5/5] Throughput test (100000 msgs)"
"$SCRIPT_DIR/run_throughput_test.sh" 100000 500
