# Makefile for the Campus Voice OS module.
#   Linux / macOS / WSL : make
#   Windows (MinGW gcc) : mingw32-make     (or run the gcc command shown in README.md)
# -O0 keeps the race-condition experiment honest (no optimizer tricks).

CC      = gcc
SRC     = main.c process_management.c scheduling.c synchronization.c deadlock.c
CFLAGS  = -Wall -Wextra -std=c11 -O0 -g

ifeq ($(OS),Windows_NT)
    TARGET  = campusvoice_os.exe
    LDFLAGS =
    RM      = del /Q
else
    TARGET  = campusvoice_os
    LDFLAGS = -pthread
    RM      = rm -f
endif

all: $(TARGET)

$(TARGET): $(SRC) process.h
	$(CC) $(CFLAGS) $(SRC) -o $(TARGET) $(LDFLAGS)

clean:
	$(RM) $(TARGET)

.PHONY: all clean
