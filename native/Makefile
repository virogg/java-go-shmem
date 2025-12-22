LIB_NAME=java_go_ipc_posix_shm

UNAME_S := $(shell uname -s)

ifeq ($(UNAME_S),Darwin)
  LIB_EXT=dylib
  JNI_OS=darwin
else
  LIB_EXT=so
  JNI_OS=linux
endif

JAVA_HOME ?= $(shell /usr/libexec/java_home 2>/dev/null)

CC ?= cc
CFLAGS ?= -O2 -fPIC

all: lib$(LIB_NAME).$(LIB_EXT)

lib$(LIB_NAME).$(LIB_EXT): posix_shm.c
	@if [ -z "$(JAVA_HOME)" ]; then echo "JAVA_HOME not set"; exit 1; fi
	$(CC) $(CFLAGS) \
		-I"$(JAVA_HOME)/include" -I"$(JAVA_HOME)/include/$(JNI_OS)" \
		-shared -o $@ $<

clean:
	rm -f lib$(LIB_NAME).so lib$(LIB_NAME).dylib

