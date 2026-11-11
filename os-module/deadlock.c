/*
 * deadlock.c - matrix-based deadlock DETECTION (multiple instances per resource type).
 *
 * Data structures
 *   Available[m]     : free instances of each resource type
 *   Allocation[n][m] : instances each process currently holds
 *   Request[n][m]    : instances each process is currently waiting for
 *   Work[m]          : the simulated free resources while we run the algorithm
 *   Finish[n]        : 1 if the process is known to be able to finish
 *
 * Algorithm
 *   1. Work = Available. Finish[i] = 1 if process i holds nothing, else 0.
 *   2. Find a process i with Finish[i]==0 and Request[i] <= Work.
 *      If found: assume it runs to completion and releases everything it holds
 *      (Work += Allocation[i]; Finish[i] = 1) and repeat step 2.
 *   3. If no such process exists, stop. Every process with Finish==0 is deadlocked.
 *
 * Detection vs prevention vs avoidance
 *   Prevention : design the system so one of the four deadlock conditions can
 *                never hold (e.g. impose a global lock ordering).
 *   Avoidance  : grant a request only if the resulting state is still "safe"
 *                (Banker's algorithm); needs each process's maximum demand in advance.
 *   Detection  : allow deadlocks to happen, periodically look for them (this
 *                file), then recover (kill a process / preempt resources).
 *
 * This module is independent of the CPU scheduler: scheduling decides who gets
 * the CPU, not who gets which resources, so scheduling alone cannot prevent deadlock.
 */
#include <stdio.h>
#include <string.h>
#include "process.h"

/* Returns the number of deadlocked processes (0 = no deadlock).
 * deadlocked[i] is set to 1 for each deadlocked process. */
int detect_deadlock(const ResourceState *rs, int deadlocked[MAX_RES_PROCS], int verbose)
{
    int work[MAX_RES_TYPES];
    int finish[MAX_RES_PROCS];
    int n = rs->num_processes, m = rs->num_resources;
    int i, j, progress, count = 0, step = 0;

    for (j = 0; j < m; j++) work[j] = rs->available[j];

    for (i = 0; i < n; i++) {                   /* processes holding nothing can't be in a deadlock */
        int holds = 0;
        for (j = 0; j < m; j++) if (rs->allocation[i][j] > 0) holds = 1;
        finish[i] = holds ? 0 : 1;
    }

    do {
        progress = 0;
        for (i = 0; i < n; i++) {
            int can_finish = 1;
            if (finish[i]) continue;
            for (j = 0; j < m; j++)
                if (rs->request[i][j] > work[j]) { can_finish = 0; break; }
            if (can_finish) {
                for (j = 0; j < m; j++) work[j] += rs->allocation[i][j];   /* release resources */
                finish[i] = 1;
                progress = 1;
                step++;
                if (verbose) {
                    printf("  Step %d: P%d can finish; releases its resources. Work = [", step, i);
                    for (j = 0; j < m; j++) printf("%s%d", j ? " " : "", work[j]);
                    printf("]\n");
                }
            }
        }
    } while (progress);

    for (i = 0; i < n; i++) {
        deadlocked[i] = !finish[i];
        if (deadlocked[i]) count++;
    }
    return count;
}

/* Two built-in test cases (3 resource types A, B, C; 5 processes). */
void load_deadlock_case(ResourceState *rs, int case_number)
{
    static const int alloc[5][3] = {{0,1,0},{2,0,0},{3,0,3},{2,1,1},{0,0,2}};
    static const int req_safe[5][3] = {{0,0,0},{2,0,2},{0,0,0},{1,0,0},{0,0,2}};
    static const int req_dead[5][3] = {{0,0,0},{2,0,2},{0,0,1},{1,0,0},{0,0,2}};
    int i, j;

    memset(rs, 0, sizeof(*rs));
    rs->num_processes = 5;
    rs->num_resources = 3;
    rs->available[0] = 0; rs->available[1] = 1; rs->available[2] = 0;
    for (i = 0; i < 5; i++)
        for (j = 0; j < 3; j++) {
            rs->allocation[i][j] = alloc[i][j];
            rs->request[i][j] = (case_number == 1) ? req_safe[i][j] : req_dead[i][j];
        }
}

/* Input format: n m / available (m ints) / allocation (n rows) / request (n rows). */
int read_resource_state(FILE *in, ResourceState *rs, char *err, size_t err_size)
{
    int i, j;
    memset(rs, 0, sizeof(*rs));
    if (fscanf(in, "%d %d", &rs->num_processes, &rs->num_resources) != 2) {
        snprintf(err, err_size, "expected: <processes> <resource types>");
        return -1;
    }
    if (rs->num_processes < 1 || rs->num_processes > MAX_RES_PROCS ||
        rs->num_resources < 1 || rs->num_resources > MAX_RES_TYPES) {
        snprintf(err, err_size, "processes must be 1..%d and resource types 1..%d",
                 MAX_RES_PROCS, MAX_RES_TYPES);
        return -1;
    }
    for (j = 0; j < rs->num_resources; j++)
        if (fscanf(in, "%d", &rs->available[j]) != 1 || rs->available[j] < 0) {
            snprintf(err, err_size, "invalid Available vector"); return -1;
        }
    for (i = 0; i < rs->num_processes; i++)
        for (j = 0; j < rs->num_resources; j++)
            if (fscanf(in, "%d", &rs->allocation[i][j]) != 1 || rs->allocation[i][j] < 0) {
                snprintf(err, err_size, "invalid Allocation matrix"); return -1;
            }
    for (i = 0; i < rs->num_processes; i++)
        for (j = 0; j < rs->num_resources; j++)
            if (fscanf(in, "%d", &rs->request[i][j]) != 1 || rs->request[i][j] < 0) {
                snprintf(err, err_size, "invalid Request matrix"); return -1;
            }
    return 0;
}

static void print_matrix(const char *name, int rows, int cols, const int mat[MAX_RES_PROCS][MAX_RES_TYPES])
{
    int i, j;
    printf("%s:\n", name);
    for (i = 0; i < rows; i++) {
        printf("  P%d:", i);
        for (j = 0; j < cols; j++) printf(" %d", mat[i][j]);
        printf("\n");
    }
}

/* Prints the state, runs detection, prints the verdict. Returns number deadlocked. */
int run_deadlock_case(const ResourceState *rs, const char *title)
{
    int deadlocked[MAX_RES_PROCS] = {0};
    int count, i, j;

    printf("\n==== Deadlock detection: %s ====\n", title);
    printf("Available:");
    for (j = 0; j < rs->num_resources; j++) printf(" %d", rs->available[j]);
    printf("\n");
    print_matrix("Allocation", rs->num_processes, rs->num_resources, rs->allocation);
    print_matrix("Request", rs->num_processes, rs->num_resources, rs->request);

    printf("Running detection:\n");
    count = detect_deadlock(rs, deadlocked, 1);

    if (count == 0) {
        printf("RESULT: NO DEADLOCK - all processes can finish.\n");
    } else {
        printf("RESULT: DEADLOCK DETECTED - %d process(es) cannot finish:", count);
        for (i = 0; i < rs->num_processes; i++)
            if (deadlocked[i]) printf(" P%d", i);
        printf("\n");
    }
    return count;
}
