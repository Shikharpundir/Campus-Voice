import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Launches the C executable with ProcessBuilder.
 *
 * Protocol (Java -> C): command-line arguments choose the mode, and scheduling jobs are
 * written to the C program's STANDARD INPUT, one per line:
 *      pid complaint_id arrival_time burst_time priority aging_days
 * Protocol (C -> Java): the report is read from STANDARD OUTPUT; stderr is merged into it.
 * A non-zero exit code is treated as a failure.
 *
 * Executable location (nothing is hardcoded to one OS):
 *   1. environment variable CV_OS_EXE, if set; otherwise
 *   2. ../os-module/campusvoice_os (".exe" is appended on Windows).
 */
public class OSModuleService {

    private static final long TIMEOUT_SECONDS = 30;
    private final File executable;

    public OSModuleService() {
        String configured = System.getenv("CV_OS_EXE");
        if (configured != null && !configured.trim().isEmpty()) {
            executable = new File(configured.trim());
        } else {
            boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            executable = new File("../os-module/campusvoice_os" + (windows ? ".exe" : ""));
        }
    }

    public File getExecutable() { return executable; }

    /** Converts database-derived tasks into the C module's input format. */
    public static String buildSchedulerInput(List<ComplaintService.SchedulingTask> tasks) {
        StringBuilder sb = new StringBuilder();
        for (ComplaintService.SchedulingTask t : tasks) {
            sb.append(t.pid).append(' ').append(t.complaintId).append(' ')
              .append(t.arrivalDay).append(' ').append(t.burst).append(' ')
              .append(t.priorityNumber).append(' ').append(t.agingDays).append('\n');
        }
        return sb.toString();
    }

    /** algorithm: "fcfs", "priority" or "both". */
    public String runScheduler(String algorithm, String stdinText) throws IOException, InterruptedException {
        return run(stdinText, "sched", algorithm);
    }

    /** Runs any C mode that needs no stdin, e.g. "sync", "race", "sched-demo", "deadlock 1". */
    public String runDemo(String... args) throws IOException, InterruptedException {
        return run(null, args);
    }

    private String run(String stdinText, String... args) throws IOException, InterruptedException {
        if (!executable.isFile()) {
            throw new IOException("C executable not found at '" + executable.getAbsolutePath()
                    + "'. Build it first (see README) or set CV_OS_EXE.");
        }
        List<String> command = new ArrayList<>();
        command.add(executable.getAbsolutePath());
        for (String a : args) command.add(a);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (OutputStream out = process.getOutputStream()) {      // send input, then close it (EOF)
            if (stdinText != null) out.write(stdinText.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // The C program may exit early on bad input; its message is read below.
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
        }

        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("C module timed out after " + TIMEOUT_SECONDS + " seconds.");
        }
        int exit = process.exitValue();
        if (exit != 0) {
            throw new IOException("C module failed (exit code " + exit + "):\n" + output);
        }
        return output.toString();
    }
}
