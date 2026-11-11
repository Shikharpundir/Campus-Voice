/*
 * process.h - shared declarations for the Campus Voice OS module.
 *
 * Priority convention used everywhere: SMALLER number = HIGHER priority
 * (priority 1 runs before priority 3).
 */
#ifndef PROCESS_H
#define PROCESS_H

#include <stdio.h>
#include <stddef.h>

#define MAX_PROCESSES 50
#define MAX_RES_PROCS 10   /* deadlock module: max processes  */
#define MAX_RES_TYPES 10   /* deadlock module: max resource types */

/* ---------- Module B: process representation ---------- */

typedef enum {
    STATE_NEW,         /* created, has not arrived yet                 */
    STATE_READY,       /* arrived and waiting for the CPU              */
    STATE_RUNNING,     /* currently on the (single) CPU                */
    STATE_TERMINATED   /* finished                                     */
} ProcessState;

/* One complaint-processing job. */
typedef struct {
    int pid;              /* process id (unique)                */
    int complaint_id;     /* complaint this job processes       */
    int arrival_time;     /* time the job enters the system     */
    int aging_days;       /* real-world days already waiting before simulation */
    int burst_time;       /* CPU time needed                    */
    int priority;         /* smaller = more important          */
    int start_time;       /* first time it gets the CPU         */
    int completion_time;  /* time it finishes                   */
    int turnaround_time;  /* completion - arrival               */
    int waiting_time;     /* turnaround - burst                 */
    ProcessState state;
} Process;

typedef struct {
    Process items[MAX_PROCESSES];
    int count;
} ProcessTable;

/* process_management.c */
const char *state_name(ProcessState state);
void table_init(ProcessTable *table);
int  create_process(ProcessTable *table, int pid, int complaint_id,
                    int arrival, int burst, int priority,
                    char *err, size_t err_size);
int  load_processes(FILE *in, ProcessTable *table, char *err, size_t err_size);
void load_demo_processes(ProcessTable *table);
void print_process_table(const ProcessTable *table);

/* scheduling.c */
typedef enum { SCHED_FCFS, SCHED_PRIORITY } SchedAlgo;
void run_scheduler(ProcessTable *table, SchedAlgo algo);

/* synchronization.c */
int run_sync_demo(int use_mutex, int iterations_per_thread);

/* deadlock.c */
typedef struct {
    int num_processes;
    int num_resources;
    int available[MAX_RES_TYPES];
    int allocation[MAX_RES_PROCS][MAX_RES_TYPES];
    int request[MAX_RES_PROCS][MAX_RES_TYPES];
} ResourceState;

int  detect_deadlock(const ResourceState *rs, int deadlocked[MAX_RES_PROCS], int verbose);
void load_deadlock_case(ResourceState *rs, int case_number);
int  read_resource_state(FILE *in, ResourceState *rs, char *err, size_t err_size);
int  run_deadlock_case(const ResourceState *rs, const char *title);

#endif /* PROCESS_H */
