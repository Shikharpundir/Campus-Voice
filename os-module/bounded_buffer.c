/*
 * bounded_buffer.c - producer/consumer synchronization using a fixed queue.
 *
 * Producers wait when the buffer is full; consumers wait when it is empty.
 * The mutex protects queue indexes and the semaphores count free/used slots.
 */
#include <stdio.h>
#include "process.h"

#ifdef _WIN32
#include <windows.h>
typedef HANDLE thread_handle;
typedef HANDLE mutex_handle;
#define WORKER_DECL(name) static DWORD WINAPI name(LPVOID arg)
static void lock_mutex(mutex_handle *m) { WaitForSingleObject(*m, INFINITE); }
static void unlock_mutex(mutex_handle *m) { ReleaseMutex(*m); }
static void wait_slot(HANDLE *s) { WaitForSingleObject(*s, INFINITE); }
static void post_slot(HANDLE *s) { ReleaseSemaphore(*s, 1, NULL); }
static int start_thread(thread_handle *t, LPTHREAD_START_ROUTINE fn, void *arg)
{ *t = CreateThread(NULL, 0, fn, arg, 0, NULL); return *t ? 0 : -1; }
#else
#include <pthread.h>
#include <semaphore.h>
typedef pthread_t thread_handle;
typedef pthread_mutex_t mutex_handle;
#define WORKER_DECL(name) static void *name(void *arg)
static void lock_mutex(mutex_handle *m) { pthread_mutex_lock(m); }
static void unlock_mutex(mutex_handle *m) { pthread_mutex_unlock(m); }
static void wait_slot(sem_t *s) { sem_wait(s); }
static void post_slot(sem_t *s) { sem_post(s); }
static int start_thread(thread_handle *t, void *(*fn)(void *), void *arg)
{ return pthread_create(t, NULL, fn, arg); }
#endif

#define BUFFER_SIZE 4
#define ITEMS_PER_PRODUCER 8

static int buffer[BUFFER_SIZE];
static int in_index;
static int out_index;
static mutex_handle buffer_mutex;
#ifdef _WIN32
static HANDLE empty_slots;
static HANDLE full_slots;
#else
static sem_t empty_slots;
static sem_t full_slots;
#endif

WORKER_DECL(producer_thread)
{
    int i;
    (void)arg;
    for (i = 1; i <= ITEMS_PER_PRODUCER; i++) {
        wait_slot(&empty_slots);
        lock_mutex(&buffer_mutex);
        buffer[in_index] = i;
        printf("Producer produced item %d at slot %d\n", i, in_index);
        in_index = (in_index + 1) % BUFFER_SIZE;
        unlock_mutex(&buffer_mutex);
        post_slot(&full_slots);
    }
#ifdef _WIN32
    return 0;
#else
    return NULL;
#endif
}

WORKER_DECL(consumer_thread)
{
    int i, item;
    (void)arg;
    for (i = 0; i < ITEMS_PER_PRODUCER; i++) {
        wait_slot(&full_slots);
        lock_mutex(&buffer_mutex);
        item = buffer[out_index];
        printf("Consumer consumed item %d from slot %d\n", item, out_index);
        out_index = (out_index + 1) % BUFFER_SIZE;
        unlock_mutex(&buffer_mutex);
        post_slot(&empty_slots);
    }
#ifdef _WIN32
    return 0;
#else
    return NULL;
#endif
}

int run_bounded_buffer_demo(void)
{
    thread_handle producer, consumer;
    in_index = 0;
    out_index = 0;
#ifdef _WIN32
    buffer_mutex = CreateMutex(NULL, FALSE, NULL);
    empty_slots = CreateSemaphore(NULL, BUFFER_SIZE, BUFFER_SIZE, NULL);
    full_slots = CreateSemaphore(NULL, 0, BUFFER_SIZE, NULL);
    if (!buffer_mutex || !empty_slots || !full_slots) return -1;
#else
    if (pthread_mutex_init(&buffer_mutex, NULL) != 0
            || sem_init(&empty_slots, 0, BUFFER_SIZE) != 0
            || sem_init(&full_slots, 0, 0) != 0) return -1;
#endif
    printf("\n==== Bounded buffer producer/consumer demo ====\n");
    printf("Buffer capacity: %d; each thread processes %d items\n", BUFFER_SIZE, ITEMS_PER_PRODUCER);
    if (start_thread(&producer, producer_thread, NULL) != 0
            || start_thread(&consumer, consumer_thread, NULL) != 0) return -1;
#ifdef _WIN32
    WaitForSingleObject(producer, INFINITE);
    WaitForSingleObject(consumer, INFINITE);
    CloseHandle(producer);
    CloseHandle(consumer);
    CloseHandle(buffer_mutex);
    CloseHandle(empty_slots);
    CloseHandle(full_slots);
#else
    pthread_join(producer, NULL);
    pthread_join(consumer, NULL);
    pthread_mutex_destroy(&buffer_mutex);
    sem_destroy(&empty_slots);
    sem_destroy(&full_slots);
#endif
    printf("Result: producer and consumer completed without overflow or underflow.\n");
    return 0;
}
