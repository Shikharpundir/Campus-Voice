/*
 * main.c - command line front end for the Campus Voice OS module.
 *
 * Usage:
 *   campusvoice_os sched-demo
 *   campusvoice_os sched <fcfs|priority|both> [file|-]    ("-" or no file = read stdin)
 *   campusvoice_os sync
 *   campusvoice_os race
 *   campusvoice_os deadlock <1|2|stdin>
 *   campusvoice_os help
 *
 * Exit codes: 0 success, 1 bad usage or bad input, 2 runtime failure.
 */
#include <stdio.h>
#include <string.h>
#include "process.h"

#define SYNC_ITERATIONS 100000

static void usage(void)
{
    fprintf(stderr,
        "Usage:\n"
        "  campusvoice_os sched-demo\n"
        "  campusvoice_os sched <fcfs|priority|both> [file|-]\n"
        "      input: pid complaint_id arrival burst priority [aging_days] ('#' = comment)\n"
        "  campusvoice_os sync                 (threads WITH mutex)\n"
        "  campusvoice_os race                 (threads WITHOUT mutex)\n"
        "  campusvoice_os deadlock <1|2|stdin> (1 = no deadlock, 2 = deadlock)\n");
}

static int cmd_sched(int argc, char **argv)
{
    ProcessTable table, copy;
    char err[160];
    FILE *in = stdin;
    int do_fcfs, do_prio;

    if (argc < 3) { usage(); return 1; }
    do_fcfs = strcmp(argv[2], "fcfs") == 0 || strcmp(argv[2], "both") == 0;
    do_prio = strcmp(argv[2], "priority") == 0 || strcmp(argv[2], "both") == 0;
    if (!do_fcfs && !do_prio) { usage(); return 1; }

    if (argc >= 4 && strcmp(argv[3], "-") != 0) {
        in = fopen(argv[3], "r");
        if (in == NULL) { fprintf(stderr, "Error: cannot open '%s'\n", argv[3]); return 1; }
    }
    if (load_processes(in, &table, err, sizeof(err)) != 0) {
        fprintf(stderr, "Input error: %s\n", err);
        if (in != stdin) fclose(in);
        return 1;
    }
    if (in != stdin) fclose(in);

    printf("Loaded %d process(es):\n", table.count);
    print_process_table(&table);
    if (do_fcfs) { copy = table; run_scheduler(&copy, SCHED_FCFS); }
    if (do_prio) { copy = table; run_scheduler(&copy, SCHED_PRIORITY); }
    return 0;
}

static int cmd_deadlock(int argc, char **argv)
{
    ResourceState rs;
    char err[160];

    if (argc < 3) { usage(); return 1; }
    if (strcmp(argv[2], "1") == 0) {
        load_deadlock_case(&rs, 1);
        run_deadlock_case(&rs, "Test case 1 (safe state)");
    } else if (strcmp(argv[2], "2") == 0) {
        load_deadlock_case(&rs, 2);
        run_deadlock_case(&rs, "Test case 2 (deadlock)");
    } else if (strcmp(argv[2], "stdin") == 0) {
        if (read_resource_state(stdin, &rs, err, sizeof(err)) != 0) {
            fprintf(stderr, "Input error: %s\n", err);
            return 1;
        }
        run_deadlock_case(&rs, "Custom input");
    } else { usage(); return 1; }
    return 0;
}

int main(int argc, char **argv)
{
    ProcessTable table;

    if (argc < 2 || strcmp(argv[1], "help") == 0) { usage(); return argc < 2 ? 1 : 0; }

    if (strcmp(argv[1], "sched-demo") == 0) {
        load_demo_processes(&table);
        printf("Built-in demo: 5 complaint-processing jobs\n");
        print_process_table(&table);
        ProcessTable copy = table;
        run_scheduler(&copy, SCHED_FCFS);
        copy = table;
        run_scheduler(&copy, SCHED_PRIORITY);
        return 0;
    }
    if (strcmp(argv[1], "sched") == 0)    return cmd_sched(argc, argv);
    if (strcmp(argv[1], "sync") == 0)     return run_sync_demo(1, SYNC_ITERATIONS) == 0 ? 0 : 2;
    if (strcmp(argv[1], "race") == 0)     return run_sync_demo(0, SYNC_ITERATIONS) == 0 ? 0 : 2;
    if (strcmp(argv[1], "deadlock") == 0) return cmd_deadlock(argc, argv);

    usage();
    return 1;
}
