/*
 * synchronization.c - two threads incrementing a shared "completed tasks" counter.
 *
 * RACE CONDITION: counter++ is really three steps (read, add, write). If two
 * threads interleave these steps, one update overwrites the other and is lost.
 * MUTUAL EXCLUSION: a mutex lets only one thread inside the CRITICAL SECTION
 * (the code touching the shared counter) at a time, so no update is lost.
 *
 * PORTABILITY
 *   Windows (MinGW / MSVC): native Win32 threads and a Win32 mutex object.
 *   Linux / macOS / WSL   : POSIX pthreads (compile with -pthread).
 * Plain pthreads are NOT available in every Windows compiler, so we do not
 * rely on them there.
 */
#include <stdio.h>
#include <stdlib.h>
#include "process.h"

#ifdef _WIN32
  #include <windows.h>
  typedef HANDLE thread_handle;
  typedef HANDLE mutex_handle;
  #define WORKER_DECL(name) static DWORD WINAPI name(LPVOID arg)
  #define WORKER_OK 0
  typedef DWORD (WINAPI *worker_fn)(LPVOID);

  static int  mutex_create(mutex_handle *m)  { *m = CreateMutex(NULL, FALSE, NULL); return *m ? 0 : -1; }
  static void mutex_lock(mutex_handle *m)    { WaitForSingleObject(*m, INFINITE); }
  static void mutex_unlock(mutex_handle *m)  { ReleaseMutex(*m); }
  static void mutex_destroy(mutex_handle *m) { CloseHandle(*m); }
  static int  thread_start(thread_handle *t, worker_fn fn, void *arg)
              { *t = CreateThread(NULL, 0, fn, arg, 0, NULL); return *t ? 0 : -1; }
  static void thread_join(thread_handle *t)  { WaitForSingleObject(*t, INFINITE); CloseHandle(*t); }
#else
  #include <pthread.h>
  typedef pthread_t       thread_handle;
  typedef pthread_mutex_t mutex_handle;
  #define WORKER_DECL(name) static void *name(void *arg)
  #define WORKER_OK NULL
  typedef void *(*worker_fn)(void *);

  static int  mutex_create(mutex_handle *m)  { return pthread_mutex_init(m, NULL); }
  static void mutex_lock(mutex_handle *m)    { pthread_mutex_lock(m); }
  static void mutex_unlock(mutex_handle *m)  { pthread_mutex_unlock(m); }
  static void mutex_destroy(mutex_handle *m) { pthread_mutex_destroy(m); }
  static int  thread_start(thread_handle *t, worker_fn fn, void *arg)
              { return pthread_create(t, NULL, fn, arg); }
  static void thread_join(thread_handle *t)  { pthread_join(*t, NULL); }
#endif

typedef struct {
    int worker_id;
    int iterations;
    int use_mutex;
} WorkerArgs;

/* Shared data. `volatile` stops the compiler from caching it in a register. */
static volatile long completed_tasks = 0;
static mutex_handle  counter_mutex;

WORKER_DECL(worker_thread)
{
    WorkerArgs *a = (WorkerArgs *)arg;
    int i;
    for (i = 0; i < a->iterations; i++) {
        if (a->use_mutex)
            mutex_lock(&counter_mutex);        /* enter critical section */

        /* ---- critical section: read, modify, write ---- */
        long tmp = completed_tasks;
        tmp = tmp + 1;
        completed_tasks = tmp;
        /* ---- end critical section ---- */

        if (a->use_mutex)
            mutex_unlock(&counter_mutex);      /* leave critical section */
    }
    return WORKER_OK;
}

/* Returns 0 on success, -1 if threads/mutex could not be created. */
int run_sync_demo(int use_mutex, int iterations_per_thread)
{
    thread_handle threads[2];
    WorkerArgs args[2];
    long expected = 2L * iterations_per_thread, lost;
    int i, started = 0;

    completed_tasks = 0;
    printf("\n==== Synchronization demo (%s) ====\n",
           use_mutex ? "WITH mutex" : "WITHOUT mutex - race condition experiment");
    printf("2 worker threads, each completing %d simulated tasks\n", iterations_per_thread);

    if (use_mutex && mutex_create(&counter_mutex) != 0) {
        fprintf(stderr, "Error: could not create mutex\n");
        return -1;
    }

    for (i = 0; i < 2; i++) {
        args[i].worker_id = i + 1;
        args[i].iterations = iterations_per_thread;
        args[i].use_mutex = use_mutex;
        if (thread_start(&threads[i], worker_thread, &args[i]) != 0) {
            fprintf(stderr, "Error: could not create worker thread %d\n", i + 1);
            break;
        }
        printf("Worker %d started\n", i + 1);
        started++;
    }

    for (i = 0; i < started; i++) {             /* wait for both threads */
        thread_join(&threads[i]);
        printf("Worker %d finished\n", i + 1);
    }

    if (use_mutex) mutex_destroy(&counter_mutex);   /* release the mutex */
    if (started != 2) return -1;

    lost = expected - completed_tasks;
    printf("Expected counter : %ld\n", expected);
    printf("Actual counter   : %ld\n", (long)completed_tasks);
    if (lost == 0)
        printf("Result: counter is correct.%s\n",
               use_mutex ? " The mutex guaranteed mutual exclusion."
                         : " No race was observed this run - it is possible but NOT guaranteed every time.");
    else
        printf("Result: %ld updates were LOST - a race condition occurred.\n", lost);
    return 0;
}
