#!/bin/bash
# Reset ring buffer files
source "$(dirname "$0")/common.sh"

cleanup_rings
log_info "Rings reset"
