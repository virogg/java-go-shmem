#!/bin/bash
# Run Java echo service (consumer from go2java, producer to java2go)
source "$(dirname "$0")/common.sh"

check_java

CMD="java $JAVA_OPTS -cp $(get_java_cp) com.jgshmem.example.JavaEchoService $CONFIG_FILE"
echo "$CMD"
exec $CMD
