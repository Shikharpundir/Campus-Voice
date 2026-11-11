import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import javax.swing.SwingUtilities;

/** Account-based console workflow for students and administrators. */
public class Main {

    private static final Scanner IN = new Scanner(System.in);
    private static final ComplaintService complaints = new ComplaintService();
    private static final OSModuleService osModule = new OSModuleService();

    public static void main(String[] args) {
        if (args.length > 0 && "--console".equalsIgnoreCase(args[0])) {
            runConsoleApp();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                new CampusVoiceSwingApp();
            } catch (Exception ex) {
                ex.printStackTrace();
                System.err.println("Failed to start Campus Voice Swing app: " + ex.getMessage());
            }
        });
    }

    private static void runConsoleApp() {
        System.out.println("=== Campus Voice ===");
        boolean running = true;
        while (running && IN.hasNextLine()) {
            System.out.println("\n1. Sign in");
            System.out.println("2. First-time staff setup");
            System.out.println("0. Exit");
            int choice = readInt("Choose: ", 0, 2);
            try {
                switch (choice) {
                    case 1:
                        UserAccount account = signIn();
                        if (account != null) openDashboard(account);
                        break;
                    case 2:
                        UserAccount newStaff = setupStaffFirstSignIn();
                        if (newStaff != null) openDashboard(newStaff);
                        break;
                    default:
                        running = false;
                        break;
                }
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid input: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("OS module error: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Interrupted while waiting for the OS module.");
                running = false;
            }
        }
        System.out.println("Goodbye.");
    }

    private static UserAccount signIn() throws SQLException {
        String email = readLine("Email: ");
        char[] password = readLine("Password: ").toCharArray();
        try {
            UserAccount account = complaints.authenticate(email, password);
            if (account == null) {
                System.out.println("Email or password is incorrect.");
                return null;
            }
            return account;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static UserAccount setupStaffFirstSignIn() throws SQLException {
        String name = readLine("Name as entered by the administrator: ");
        String email = readLine("Staff email: ");
        UserAccount pending = complaints.findPendingStaff(name, email);
        if (pending == null) {
            System.out.println("No pending staff login matches that name and email.");
            return null;
        }
        System.out.println("Staff account verified for " + pending.name + ".");
        Integer departmentId = readIdFromOptions(
                "Choose your department (saved for future sign-ins): ",
                complaints.listDepartments(), "department");
        if (departmentId == null) return null;

        char[] password = readLine("Create a password (minimum 8 characters): ").toCharArray();
        char[] confirmation = readLine("Confirm password: ").toCharArray();
        try {
            if (!Arrays.equals(password, confirmation)) {
                System.out.println("The passwords do not match.");
                return null;
            }
            UserAccount account = complaints.activateStaffAccount(pending, email, password, departmentId);
            System.out.println("Setup complete. Department saved: " + account.departmentName);
            return account;
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }

    private static void openDashboard(UserAccount account)
            throws SQLException, IOException, InterruptedException {
        System.out.println("\nWelcome, " + account.name + ".");
        if ("ADMIN".equals(account.role)) {
            openAdminDashboard(account);
        } else if ("STAFF".equals(account.role)) {
            openStaffDashboard(account);
        } else {
            openStudentDashboard(account);
        }
    }

    private static void openStudentDashboard(UserAccount account) throws SQLException {
        boolean signedIn = true;
        while (signedIn && IN.hasNextLine()) {
            System.out.println("\n1. Submit a complaint");
            System.out.println("2. View my complaints");
            System.out.println("0. Sign out");
            int choice = readInt("Choose: ", 0, 2);
            switch (choice) {
                case 1: submitComplaint(account); break;
                case 2: displayComplaints(complaints.listComplaintsFor(account)); break;
                default: signedIn = false; break;
            }
        }
    }

    private static void openAdminDashboard(UserAccount account)
            throws SQLException, IOException, InterruptedException {
        boolean signedIn = true;
        while (signedIn && IN.hasNextLine()) {
            System.out.println("\n1. View all complaints");
            System.out.println("2. Update complaint status");
            System.out.println("3. Assign complaint to department staff");
            System.out.println("4. Run C scheduler on open complaints");
            System.out.println("5. Run OS demos");
            System.out.println("0. Sign out");
            int choice = readInt("Choose: ", 0, 5);
            switch (choice) {
                case 1: displayComplaints(complaints.listComplaintsFor(account)); break;
                case 2: updateStatus(account); break;
                case 3: assignComplaintToStaff(account); break;
                case 4: runSchedulerOnDatabase(account); break;
                case 5: runOsDemos(); break;
                default: signedIn = false; break;
            }
        }
    }

    private static void openStaffDashboard(UserAccount account) throws SQLException {
        boolean signedIn = true;
        while (signedIn && IN.hasNextLine()) {
            System.out.println("\n1. View complaints assigned to me (" + account.departmentName + ")");
            System.out.println("2. Update an assigned complaint status");
            System.out.println("0. Sign out");
            int choice = readInt("Choose: ", 0, 2);
            switch (choice) {
                case 1: displayComplaints(complaints.listComplaintsFor(account)); break;
                case 2: updateStatus(account); break;
                default: signedIn = false; break;
            }
        }
    }

    private static void submitComplaint(UserAccount account) throws SQLException {
        List<String[]> departments = complaints.listDepartments();
        Integer departmentId = readIdFromOptions("Department id: ", departments, "department");
        if (departmentId == null) return;
        String title = readLine("Title: ");
        String description = readLine("Description: ");
        String location = readLine("Location: ");
        String priority = readLine("Priority (LOW/MEDIUM/HIGH/URGENT): ").toUpperCase();
        int id = complaints.submitComplaint(account.userId, departmentId,
                title, description, location, priority);
        System.out.println("Complaint submitted. Complaint id = " + id);
    }

    private static Integer readIdFromOptions(String prompt, List<String[]> options, String type) {
        if (options.isEmpty()) {
            System.out.println("Cannot continue: no " + type + "s are configured in the database.");
            return null;
        }
        for (String[] option : options) {
            System.out.printf("  %s - %s%n", option[0], option[1]);
        }
        while (IN.hasNextLine()) {
            String input = readLine(prompt);
            try {
                int selectedId = Integer.parseInt(input);
                for (String[] option : options) {
                    if (selectedId == Integer.parseInt(option[0])) return selectedId;
                }
            } catch (NumberFormatException ignored) { }
            System.out.println("Please enter an ID from the list above.");
        }
        return null;
    }

    private static void displayComplaints(List<String[]> rows) {
        if (rows.isEmpty()) {
            System.out.println("No complaints found.");
            return;
        }
        System.out.printf("%-4s %-26s %-8s %-12s %-16s %-22s %-14s%n",
                "ID", "Title", "Priority", "Status", "Student", "Department", "Assigned to");
        for (String[] row : rows) {
            System.out.printf("%-4s %-26s %-8s %-12s %-16s %-22s %-14s%n",
                    row[0], cut(row[1], 26), row[2], row[3],
                    cut(row[4], 16), cut(row[5], 22), cut(row[6], 14));
        }
    }

    private static void updateStatus(UserAccount account) throws SQLException {
        displayComplaints(complaints.listComplaintsFor(account));
        int complaintId = readInt("Complaint id to handle: ", 1, Integer.MAX_VALUE);
        String status = readLine("New status (IN_PROGRESS/RESOLVED/REJECTED): ").toUpperCase();
        String remarks = readLine("Remarks (optional): ");
        String old = complaints.updateStatus(complaintId, status, account, remarks);
        System.out.println("Updated complaint " + complaintId + ": " + old + " -> " + status
                + ".");
    }

    private static void assignComplaintToStaff(UserAccount admin) throws SQLException {
        int complaintId = readInt("Complaint id to assign: ", 1, Integer.MAX_VALUE);
        List<String[]> staff = complaints.listStaffForComplaint(complaintId, admin);
        if (staff.isEmpty()) {
            System.out.println("No staff accounts are configured for that complaint's department.");
            return;
        }
        System.out.println("Staff in this complaint's department:");
        for (String[] member : staff) {
            System.out.printf("  %s - %s (%s) [%s]%n", member[0], member[1], member[2], member[3]);
        }
        int staffId = readInt("Staff id to assign: ", 1, Integer.MAX_VALUE);
        boolean found = false;
        for (String[] member : staff) {
            if (Integer.parseInt(member[0]) == staffId) {
                found = true;
                break;
            }
        }
        if (!found) {
            System.out.println("Choose a staff id from the list above.");
            return;
        }
        complaints.assignComplaint(complaintId, staffId, admin);
        System.out.println("Complaint " + complaintId + " assigned to staff id " + staffId + ".");
    }

    private static void runSchedulerOnDatabase(UserAccount account)
            throws SQLException, IOException, InterruptedException {
        List<ComplaintService.SchedulingTask> tasks = complaints.loadOpenComplaintsForScheduling(account);
        if (tasks.isEmpty()) {
            System.out.println("There are no open complaints to schedule.");
            return;
        }

        System.out.println("Open complaints read from MySQL (priority/burst mapping: URGENT=1/2, HIGH=2/3, MEDIUM=3/5, LOW=4/8):");
        for (ComplaintService.SchedulingTask task : tasks) {
            System.out.printf("  P%d <- complaint %d (%s, %s) arrival day %d%n",
                    task.pid, task.complaintId, task.priorityName, cut(task.title, 30), task.arrivalDay);
        }
        String algorithm = readLine("Algorithm (fcfs / priority / both) [both]: ").toLowerCase();
        if (algorithm.isEmpty()) algorithm = "both";
        if (!algorithm.equals("fcfs") && !algorithm.equals("priority") && !algorithm.equals("both")) {
            throw new IllegalArgumentException("Algorithm must be fcfs, priority or both.");
        }
        System.out.println(osModule.runScheduler(algorithm, OSModuleService.buildSchedulerInput(tasks)));
    }

    private static void runOsDemos() throws IOException, InterruptedException {
        System.out.println("a. Scheduler demo  b. Mutex demo  c. Race demo  d. Deadlock case 1  e. Deadlock case 2");
        switch (readLine("Choose: ").toLowerCase()) {
            case "a": System.out.println(osModule.runDemo("sched-demo")); break;
            case "b": System.out.println(osModule.runDemo("sync")); break;
            case "c": System.out.println(osModule.runDemo("race")); break;
            case "d": System.out.println(osModule.runDemo("deadlock", "1")); break;
            case "e": System.out.println(osModule.runDemo("deadlock", "2")); break;
            default: System.out.println("Unknown choice.");
        }
    }

    private static String readLine(String prompt) {
        System.out.print(prompt);
        return IN.hasNextLine() ? IN.nextLine().trim() : "";
    }

    private static int readInt(String prompt, int min, int max) {
        while (IN.hasNextLine()) {
            System.out.print(prompt);
            String input = IN.nextLine().trim();
            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) return value;
            } catch (NumberFormatException ignored) { }
            System.out.println("Please enter a whole number between " + min + " and " + max + ".");
        }
        return min;
    }

    private static String cut(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "~";
    }
}
