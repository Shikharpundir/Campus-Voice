/*
 * scheduling.c - FCFS and Non-preemptive Priority CPU scheduling (single CPU).
 *
 * HOW IT WORKS (beginner version)
 *   The CPU keeps a clock called `time`. Each time the CPU becomes free we:
 *     1. Mark every job whose arrival_time <= time as READY.
 *     2. Choose ONE ready job using the algorithm's rule.
 *     3. Run it to completion (non-preemptive: nobody interrupts it).
 *     4. If nothing is ready, the CPU is IDLE until the next job arrives.
 *   A job is never picked before it arrives, because only READY jobs are candidates.
 *
 * SELECTION RULES AND TIE-BREAKING
 *   FCFS     : earliest arrival_time first; tie -> smaller pid.
 *   PRIORITY : smaller priority number first; tie -> earlier arrival_time;
 *              still tied -> smaller pid.
 *
 * FORMULAS
 *   turnaround = completion - arrival
 *   waiting    = turnaround - burst
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "process.h"

typedef struct { int pid; int start; int end; } GanttSlot;      /* pid == -1 means IDLE */
typedef struct { int time; int seq; int pid; ProcessState from, to; } StateEvent;

#define MAX_SLOTS  (2 * MAX_PROCESSES + 1)
#define MAX_EVENTS (3 * MAX_PROCESSES)

typedef struct {
    GanttSlot  slots[MAX_SLOTS];
    int        slot_count;
    StateEvent events[MAX_EVENTS];
    int        event_count;
} ScheduleLog;

static void log_event(ScheduleLog *log, int time, int pid, ProcessState from, ProcessState to)
{
    StateEvent *e = &log->events[log->event_count];
    e->time = time; e->seq = log->event_count; e->pid = pid; e->from = from; e->to = to;
    log->event_count++;
}

static void log_slot(ScheduleLog *log, int pid, int start, int end)
{
    log->slots[log->slot_count].pid = pid;
    log->slots[log->slot_count].start = start;
    log->slots[log->slot_count].end = end;
    log->slot_count++;
}

static int effective_priority(const Process *p, int current_time)
{
    int waiting = p->aging_days + current_time - p->arrival_time;
    int aging_boost = waiting / 3; /* every 3 waiting days, gain one higher priority rank */
    int aged = p->priority - aging_boost;
    return aged < 1 ? 1 : aged;
}

/* Returns 1 if job a should run before job b under the chosen algorithm. */
static int runs_before(const Process *a, const Process *b, SchedAlgo algo, int current_time)
{
    if (algo == SCHED_PRIORITY) {
        int pa = effective_priority(a, current_time);
        int pb = effective_priority(b, current_time);
        if (pa != pb) return pa < pb;
    }
    if (a->arrival_time != b->arrival_time)
        return a->arrival_time < b->arrival_time;
    return a->pid < b->pid;
}

static int compare_events(const void *x, const void *y)
{
    const StateEvent *a = (const StateEvent *)x, *b = (const StateEvent *)y;
    if (a->time != b->time) return a->time - b->time;
    return a->seq - b->seq;
}

static void print_gantt(const ScheduleLog *log)
{
    int i;
    printf("\nGantt chart:\n");
    for (i = 0; i < log->slot_count; i++) {
        char label[16];
        int len, left, right;
        if (log->slots[i].pid < 0) snprintf(label, sizeof(label), "IDLE");
        else                       snprintf(label, sizeof(label), "P%d", log->slots[i].pid);
        len = (int)strlen(label);
        left = (6 - len) / 2;  if (left < 0) left = 0;
        right = 6 - len - left; if (right < 0) right = 0;
        printf("|%*s%s%*s", left, "", label, right, "");
    }
    printf("|\n");
    for (i = 0; i < log->slot_count; i++)
        printf("%-7d", log->slots[i].start);
    if (log->slot_count > 0)
        printf("%d", log->slots[log->slot_count - 1].end);
    printf("\n");
}

static void print_state_trace(ScheduleLog *log)
{
    int i;
    qsort(log->events, (size_t)log->event_count, sizeof(StateEvent), compare_events);
    printf("\nProcess state trace:\n");
    for (i = 0; i < log->event_count; i++) {
        const StateEvent *e = &log->events[i];
        printf("  t=%-3d P%-3d %-10s -> %s\n", e->time, e->pid,
               state_name(e->from), state_name(e->to));
    }
}

/* Runs the simulation, updates the table in place, and prints a full report. */
void run_scheduler(ProcessTable *table, SchedAlgo algo)
{
    ScheduleLog *log = (ScheduleLog *)calloc(1, sizeof(ScheduleLog));
    int n = table->count, finished = 0, time = 0, i;
    double total_wait = 0.0, total_turn = 0.0;

    if (log == NULL) { fprintf(stderr, "Error: out of memory\n"); return; }

    for (i = 0; i < n; i++) {                /* fresh start */
        Process *p = &table->items[i];
        p->state = STATE_NEW; p->start_time = -1;
        p->completion_time = p->turnaround_time = p->waiting_time = 0;
    }

    while (finished < n) {
        int best = -1, next_arrival = -1;

        /* Step 1: NEW -> READY for everything that has arrived. */
        for (i = 0; i < n; i++) {
            Process *p = &table->items[i];
            if (p->state == STATE_NEW && p->arrival_time <= time) {
                p->state = STATE_READY;
                log_event(log, p->arrival_time, p->pid, STATE_NEW, STATE_READY);
            }
        }

        /* Step 2: choose among READY jobs. */
        for (i = 0; i < n; i++) {
            const Process *p = &table->items[i];
            if (p->state == STATE_READY &&
                (best < 0 || runs_before(p, &table->items[best], algo, time)))
                best = i;
        }

        if (best < 0) {                       /* CPU idle until next arrival */
            for (i = 0; i < n; i++) {
                const Process *p = &table->items[i];
                if (p->state == STATE_NEW &&
                    (next_arrival < 0 || p->arrival_time < next_arrival))
                    next_arrival = p->arrival_time;
            }
            log_slot(log, -1, time, next_arrival);
            time = next_arrival;
            continue;
        }

        /* Step 3: run the chosen job to completion. */
        {
            Process *p = &table->items[best];
            p->state = STATE_RUNNING;
            p->start_time = time;
            log_event(log, time, p->pid, STATE_READY, STATE_RUNNING);
            log_slot(log, p->pid, time, time + p->burst_time);
            time += p->burst_time;
            p->completion_time = time;
            p->turnaround_time = p->completion_time - p->arrival_time;
            p->waiting_time    = p->turnaround_time - p->burst_time;
            p->state = STATE_TERMINATED;
            log_event(log, time, p->pid, STATE_RUNNING, STATE_TERMINATED);
            total_wait += p->waiting_time;
            total_turn += p->turnaround_time;
            finished++;
        }
    }

    printf("\n==== %s Scheduling (non-preemptive%s) ====\n",
           algo == SCHED_FCFS ? "FCFS" : "Priority",
           algo == SCHED_PRIORITY ? ", aging enabled" : "");
    printf("%-5s %-9s %-8s %-6s %-9s %-9s %-6s %-11s %-11s %-8s %-10s\n",
           "PID", "Complaint", "Arrival", "Burst", "Priority", "Effective", "Start",
           "Completion", "Turnaround", "Waiting", "State");
    for (i = 0; i < n; i++) {
        const Process *p = &table->items[i];
        printf("P%-4d C%-8d %-8d %-6d %-9d %-9d %-6d %-11d %-11d %-8d %-10s\n",
               p->pid, p->complaint_id, p->arrival_time, p->burst_time, p->priority,
               algo == SCHED_PRIORITY ? effective_priority(p, p->start_time) : p->priority,
               p->start_time, p->completion_time,
               p->turnaround_time, p->waiting_time,
               state_name(p->state));
    }
    printf("\nAverage waiting time    : %.2f\n", total_wait / n);
    printf("Average turnaround time : %.2f\n", total_turn / n);

    print_gantt(log);
    print_state_trace(log);
    free(log);
}
