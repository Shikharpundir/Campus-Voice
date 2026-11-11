/*
 * process_management.c - process (task) creation, tracking and input loading.
 *
 * In this simulation a "process" is one complaint-processing job. Creating a
 * process means validating its data and adding it to the process table in the
 * NEW state. Scheduling later moves it NEW -> READY -> RUNNING -> TERMINATED.
 *
 * Input file/stdin format (one job per line, '#' starts a comment):
 *     pid complaint_id arrival_time burst_time priority [aging_days]
 */
#include <stdio.h>
#include <string.h>
#include "process.h"

const char *state_name(ProcessState state)
{
    switch (state) {
        case STATE_NEW:        return "NEW";
        case STATE_READY:      return "READY";
        case STATE_RUNNING:    return "RUNNING";
        case STATE_TERMINATED: return "TERMINATED";
    }
    return "UNKNOWN";
}

void table_init(ProcessTable *table)
{
    memset(table, 0, sizeof(*table));
}

/* Validate and add one process. Returns 0 on success, -1 on error (err filled). */
int create_process(ProcessTable *table, int pid, int complaint_id,
                   int arrival, int burst, int priority,
                   char *err, size_t err_size)
{
    int i;
    if (table->count >= MAX_PROCESSES) {
        snprintf(err, err_size, "process table full (max %d)", MAX_PROCESSES);
        return -1;
    }
    if (pid <= 0)       { snprintf(err, err_size, "pid must be > 0 (got %d)", pid); return -1; }
    if (arrival < 0)    { snprintf(err, err_size, "P%d: arrival time must be >= 0", pid); return -1; }
    if (burst <= 0)     { snprintf(err, err_size, "P%d: burst time must be > 0", pid); return -1; }
    if (priority < 0)   { snprintf(err, err_size, "P%d: priority must be >= 0", pid); return -1; }
    for (i = 0; i < table->count; i++) {
        if (table->items[i].pid == pid) {
            snprintf(err, err_size, "duplicate pid %d", pid);
            return -1;
        }
    }

    Process *p = &table->items[table->count++];
    memset(p, 0, sizeof(*p));
    p->pid = pid;
    p->complaint_id = complaint_id;
    p->arrival_time = arrival;
    p->burst_time = burst;
    p->priority = priority;
    p->start_time = -1;
    p->state = STATE_NEW;
    return 0;
}

/* Read jobs from a stream. Returns 0 on success, -1 on error. */
int load_processes(FILE *in, ProcessTable *table, char *err, size_t err_size)
{
    char line[256];
    int line_no = 0;
    char detail[128];

    table_init(table);
    while (fgets(line, sizeof(line), in) != NULL) {
        int pid, cid, at, bt, pr, age = 0;
        char extra;
        char *hash;
        line_no++;

        hash = strchr(line, '#');           /* strip comments */
        if (hash) *hash = '\0';

        /* skip blank lines */
        char *p = line;
        while (*p == ' ' || *p == '\t' || *p == '\r' || *p == '\n') p++;
        if (*p == '\0') continue;

        int fields = sscanf(line, "%d %d %d %d %d %d %c",
                            &pid, &cid, &at, &bt, &pr, &age, &extra);
        if (fields != 5 && fields != 6) {
            snprintf(err, err_size,
                     "line %d: expected 5 or 6 integers: pid complaint_id arrival burst priority [aging_days]",
                     line_no);
            return -1;
        }
        if (fields == 5 && sscanf(line, "%d %d %d %d %d %c",
                                  &pid, &cid, &at, &bt, &pr, &extra) != 5) {
            snprintf(err, err_size, "line %d: unexpected extra input", line_no);
            return -1;
        }
        if (age < 0) {
            snprintf(err, err_size, "line %d: aging_days must be >= 0", line_no);
            return -1;
        }
        if (create_process(table, pid, cid, at, bt, pr, detail, sizeof(detail)) != 0) {
            snprintf(err, err_size, "line %d: %s", line_no, detail);
            return -1;
        }
        table->items[table->count - 1].aging_days = age;
    }
    if (table->count == 0) {
        snprintf(err, err_size, "no processes were provided");
        return -1;
    }
    return 0;
}

/* Built-in sample: five jobs with different arrival, burst and priority. */
void load_demo_processes(ProcessTable *table)
{
    static const int demo[5][5] = {
        /* pid, complaint, arrival, burst, priority */
        {1, 101, 0, 5, 3},
        {2, 102, 1, 3, 1},
        {3, 103, 2, 8, 4},
        {4, 104, 3, 2, 2},
        {5, 105, 6, 4, 1}
    };
    char err[128];
    int i;
    table_init(table);
    for (i = 0; i < 5; i++)
        create_process(table, demo[i][0], demo[i][1], demo[i][2],
                       demo[i][3], demo[i][4], err, sizeof(err));
}

void print_process_table(const ProcessTable *table)
{
    int i;
    printf("%-5s %-9s %-8s %-6s %-9s %-11s\n",
           "PID", "Complaint", "Arrival", "Burst", "Priority", "Aged(days)");
    for (i = 0; i < table->count; i++) {
        const Process *p = &table->items[i];
        printf("P%-4d C%-8d %-8d %-6d %-9d %-11d\n",
               p->pid, p->complaint_id, p->arrival_time, p->burst_time,
               p->priority, p->aging_days);
    }
}
