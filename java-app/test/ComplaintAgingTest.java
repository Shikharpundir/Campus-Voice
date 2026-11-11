import java.util.Collections;

/** Dependency-free checks for effective complaint priority aging. */
public class ComplaintAgingTest {
    public static void main(String[] args) {
        expect(4, 0, 4);
        expect(4, 2, 4);
        expect(4, 3, 3);
        expect(4, 9, 1);
        expect(1, 30, 1);
        ComplaintService.SchedulingTask task =
                new ComplaintService.SchedulingTask(1, 123, 6, "LOW", "Old complaint");
        String input = OSModuleService.buildSchedulerInput(Collections.singletonList(task));
        if (!"1 123 0 8 4 6\n".equals(input)) {
            throw new AssertionError("Scheduler input must include actual waiting days: " + input);
        }
        System.out.println("Complaint aging checks passed.");
    }

    private static void expect(int basePriority, int waitingDays, int expected) {
        int actual = ComplaintService.SchedulingTask.applyAging(basePriority, waitingDays);
        if (actual != expected) {
            throw new AssertionError("Expected effective priority " + expected + " but was " + actual);
        }
    }
}
