#!/bin/bash
# Run Go client (producer to go2java, consumer from java2go)
source "$(dirname "$0")/common.sh"

check_go

N=${1:-100000}
RESET=${2:-false}

RESET_FLAG=""
if [ "$RESET" = "true" ]; then
    RESET_FLAG="-reset"
fi

CMD="go run $GO_DIR/cmd/example/main.go -config $CONFIG_FILE -n $N $RESET_FLAG"
echo "$CMD"
exec $CMD
