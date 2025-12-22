#!/bin/bash
# Common env vars and functions for integration tests

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

GO_DIR="$PROJECT_ROOT/go"
JAVA_DIR="$PROJECT_ROOT/java"
SHARED_DIR="$PROJECT_ROOT/shared"
CONFIG_FILE="${CONFIG_FILE:-$SHARED_DIR/config.json}"
CONFIG_POSIX="$SHARED_DIR/config-posix.json"
NATIVE_DIR="$PROJECT_ROOT/native"

JAVA_OPTS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

check_java() {
    if ! command -v java &> /dev/null; then
        log_error "java not found"
        exit 1
    fi
}

check_go() {
    if ! command -v go &> /dev/null; then
        log_error "go not found"
        exit 1
    fi
}

build_java() {
    log_info "Building Java..."
    (cd "$JAVA_DIR" && mvn -q clean compile dependency:copy-dependencies -DskipTests) || {
        log_error "Java build failed"
        exit 1
    }
}

get_java_cp() {
    echo "$JAVA_DIR/target/classes:$JAVA_DIR/target/dependency/*"
}

cleanup_rings() {
    log_info "Cleaning up ring files..."
    rm -f /tmp/go2java.mmap /tmp/java2go.mmap 2>/dev/null
}

cleanup_posix_shm() {
    log_info "Cleaning up POSIX shm..."
    setup_cgo_env
    log_info "Running shm reset..."
    (cd "$GO_DIR" && go run -tags posixshm cmd/reset/main.go -config "$CONFIG_POSIX") || log_warn "reset failed"
    # Linux fallback
    rm -f /dev/shm/go2java_shm /dev/shm/java2go_shm 2>/dev/null || true
}

build_native() {
    log_info "Building native lib..."
    (cd "$NATIVE_DIR" && make) || {
        log_error "Native build failed"
        exit 1
    }
}

get_native_lib_path() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "$NATIVE_DIR/libposix_shm.dylib"
    else
        echo "$NATIVE_DIR/libposix_shm.so"
    fi
}

setup_cgo_env() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        SDK_PATH=$(xcrun --show-sdk-path 2>/dev/null || echo "/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk")
        export CGO_CFLAGS="-I${SDK_PATH}/usr/include -w"
        export CGO_LDFLAGS="-L${SDK_PATH}/usr/lib -F${SDK_PATH}/System/Library/Frameworks"
        # Use system clang for linking
        export CC=$(xcrun -find clang)
    fi
}
