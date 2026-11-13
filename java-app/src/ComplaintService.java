import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * All complaint-related database work. Every SQL statement is a PreparedStatement
 * (parameters are bound with setXxx, never concatenated), which prevents SQL injection.
 * Operations that change more than one table run inside a JDBC transaction.
 */
public class ComplaintService {

    private final CampusVoiceDAO dao = new CampusVoiceDAO();

    public static final Set<String> PRIORITIES =
            new HashSet<>(Arrays.asList("LOW", "MEDIUM", "HIGH", "URGENT"));
    public static final Set<String> STATUSES =
            new HashSet<>(Arrays.asList("PENDING", "IN_PROGRESS", "RESOLVED", "REJECTED"));

    /** One open complaint converted into a scheduling job input. */
    public static class SchedulingTask {
        public final int pid;
        public final int complaintId;
        public final int arrivalDay;
        public final int agingDays;
        public final int burst;
        public final int priorityNumber;
        public final int effectivePriority;
        public final String title;
        public final String priorityName;

        SchedulingTask(int pid, int complaintId, int agingDays, String priorityName, String title) {
            this.pid = pid;
            this.complaintId = complaintId;
            this.arrivalDay = 0;
            this.agingDays = Math.max(0, agingDays);
            this.priorityName = priorityName;
            this.title = title;
            // Assumption: the database stores no CPU burst, so we estimate one from priority.
            // Priority number: smaller = more urgent (matches the C module).
            switch (priorityName) {
                case "URGENT": this.priorityNumber = 1; this.burst = 2; break;
                case "HIGH":   this.priorityNumber = 2; this.burst = 3; break;
                case "MEDIUM": this.priorityNumber = 3; this.burst = 5; break;
                default:       this.priorityNumber = 4; this.burst = 8; break;
            }
            this.effectivePriority = applyAging(this.priorityNumber, this.agingDays);
        }

        public int getEffectivePriority() {
            return effectivePriority;
        }

        public static int applyAging(int basePriority, int waitingDays) {
            int agingBoost = Math.max(0, waitingDays / 3);
            return Math.max(1, basePriority - agingBoost);
        }
    }

    public static class ComplaintDetails {
        public final int complaintId;
        public final String title;
        public final String description;
        public final String location;
        public final String priority;
        public final String status;
        public final String student;
        public final String department;
        public final String assignee;
        public final String createdAt;
        public final int ageDays;
        public final Integer feedbackRating;
        public final String feedbackComment;
        public final List<Attachment> attachments = new ArrayList<>();

        ComplaintDetails(ResultSet rs) throws SQLException {
            complaintId = rs.getInt("complaint_id");
            title = rs.getString("title");
            description = rs.getString("description");
            location = rs.getString("location");
            priority = rs.getString("priority");
            status = rs.getString("status");
            student = rs.getString("student");
            department = rs.getString("department_name");
            assignee = rs.getString("assigned");
            createdAt = rs.getString("created_at");
            ageDays = rs.getInt("age_days");
            int rating = rs.getInt("feedback_rating");
            feedbackRating = rs.wasNull() ? null : rating;
            feedbackComment = rs.getString("feedback_comment");
        }
    }

    public static class Attachment {
        public final String fileName;
        public final String mimeType;
        public final byte[] data;

        Attachment(String fileName, String mimeType, byte[] data) {
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.data = data;
        }
    }

    /** Creates a student account. Emails are unique in the database. */
    public int registerStudent(String name, String email, char[] password) throws SQLException {
        requireText(name, "Name", 100);
        requireText(email, "Email", 150);
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters.");
        }
        String sql = "INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, 'STUDENT')";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.setString(2, email.trim().toLowerCase());
            ps.setString(3, PasswordUtil.hash(password));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No user id was generated.");
                return keys.getInt(1);
            }
        }
    }

    public int registerStaff(UserAccount admin, String name, String email) throws SQLException {
        requireAdmin(admin);
        requireText(name, "Name", 100);
        requireText(email, "Email", 150);
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        return dao.createStaffAccount(admin.userId, name, email);
    }

    public List<String[]> listStaffAccounts(UserAccount admin) throws SQLException {
        requireAdmin(admin);
        return dao.listStaffAccounts(admin.userId);
    }

    public UserAccount findPendingStaff(String name, String email) throws SQLException {
        requireText(name, "Name", 100);
        requireText(email, "Email", 150);
        return dao.findPendingStaff(name, email);
    }

    public UserAccount activateStaffAccount(UserAccount pendingStaff, String email,
                                            char[] password, int departmentId)
            throws SQLException {
        if (pendingStaff == null || !"STAFF".equals(pendingStaff.role)
                || pendingStaff.departmentId != null) {
            throw new IllegalArgumentException("A verified, pending staff account is required.");
        }
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters.");
        }
        return dao.activateStaffAccount(pendingStaff.userId, pendingStaff.name, email, password, departmentId);
    }

    /** Returns an authenticated account, or null when the email/password pair is invalid. */
    public UserAccount authenticate(String email, char[] password) throws SQLException {
        return dao.validateUser(email, password);
    }

    /** Inserts a complaint and its first history row atomically. Returns the new complaint id. */
    public int submitComplaint(int userId, int departmentId, String title, String description,
                               String location, String priority) throws SQLException {
        return submitComplaint(userId, departmentId, title, description, location, priority, null, null, null);
    }

    public int submitComplaint(int userId, int departmentId, String title, String description,
                               String location, String priority, String attachmentName,
                               String attachmentMime, byte[] attachmentData) throws SQLException {
        requireText(title, "Title", 150);
        requireText(description, "Description", 5000);
        requireText(location, "Location", 150);
        if (!PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("Priority must be one of " + PRIORITIES);
        }
        if (attachmentData != null) {
            requireText(attachmentName, "Attachment filename", 255);
            if (attachmentData.length == 0 || attachmentData.length > 10 * 1024 * 1024) {
                throw new IllegalArgumentException("Image must be between 1 byte and 10 MB.");
            }
            if (!"image/jpeg".equals(attachmentMime) && !"image/png".equals(attachmentMime)
                    && !"image/gif".equals(attachmentMime) && !"image/bmp".equals(attachmentMime)) {
                throw new IllegalArgumentException("Only JPEG, PNG, GIF, or BMP images are supported.");
            }
        }
        if (attachmentData == null) {
            return dao.createComplaint(new Complaint(userId, departmentId, title, description, location, priority));
        }

        final String insertComplaint =
                "INSERT INTO complaints (user_id, department_id, title, description, location, priority) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        final String insertHistory =
                "INSERT INTO complaint_history (complaint_id, changed_by, old_status, new_status, remarks) "
                + "VALUES (?, ?, NULL, 'PENDING', 'Complaint submitted')";

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);                       // start transaction
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT role FROM users WHERE user_id = ?")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || !"STUDENT".equals(rs.getString("role"))) {
                            throw new IllegalArgumentException("Only a student can submit a complaint.");
                        }
                    }
                }
                int complaintId;
                try (PreparedStatement ps = con.prepareStatement(insertComplaint, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, departmentId);
                    ps.setString(3, title.trim());
                    ps.setString(4, description.trim());
                    ps.setString(5, location.trim());
                    ps.setString(6, priority);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No complaint id was generated.");
                        complaintId = keys.getInt(1);
                    }
                }
                try (PreparedStatement ph = con.prepareStatement(insertHistory)) {
                    ph.setInt(1, complaintId);
                    ph.setInt(2, userId);
                    ph.executeUpdate();
                }
                if (attachmentData != null) {
                    try (PreparedStatement pa = con.prepareStatement(
                            "INSERT INTO complaint_attachments (complaint_id, file_name, mime_type, file_data) "
                                    + "VALUES (?, ?, ?, ?)")) {
                        pa.setInt(1, complaintId);
                        pa.setString(2, attachmentName);
                        pa.setString(3, attachmentMime);
                        pa.setBytes(4, attachmentData);
                        pa.executeUpdate();
                    }
                }
                con.commit();
                return complaintId;
            } catch (SQLException | RuntimeException e) {
                con.rollback();                             // undo everything if any step failed
                throw e;
            }
        }
    }

    /** Returns student accounts that can submit complaints: id and name. */
    public List<String[]> listStudents() throws SQLException {
        List<String[]> students = new ArrayList<>();
        String sql = "SELECT user_id, name FROM users WHERE role = 'STUDENT' ORDER BY user_id";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                students.add(new String[] {
                        String.valueOf(rs.getInt("user_id")), rs.getString("name") });
            }
        }
        return students;
    }

    /** Returns configured departments: id and name. */
    public List<String[]> listDepartments() throws SQLException {
        List<String[]> departments = new ArrayList<>();
        String sql = "SELECT department_id, department_name FROM departments ORDER BY department_id";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                departments.add(new String[] {
                        String.valueOf(rs.getInt("department_id")), rs.getString("department_name") });
            }
        }
        return departments;
    }

    /** Returns complaints within the authenticated user's access scope. */
    public List<String[]> listComplaintsFor(UserAccount account) throws SQLException {
        if (account == null) throw new IllegalArgumentException("Sign in is required.");
        if ("ADMIN".equals(account.role)) {
            requireAdmin(account);
            return dao.getComplaintsForAdmin(account.userId);
        }
        if ("STAFF".equals(account.role)) {
            requireStaff(account);
            return dao.getComplaintsForStaff(account.userId);
        }
        if (!"STUDENT".equals(account.role)) {
            throw new IllegalArgumentException("This account cannot view complaints.");
        }
        String sql =
                "SELECT c.complaint_id, c.title, c.priority, c.status, u.name AS student, "
                + "d.department_name, COALESCE(a.name, CONCAT(global_admin.name, ' (Global admin)'), '-') AS assigned, "
                + "DATEDIFF(CURRENT_DATE, DATE(c.created_at)) AS age_days "
                + "FROM complaints c "
                + "INNER JOIN users u ON u.user_id = c.user_id "
                + "INNER JOIN departments d ON d.department_id = c.department_id "
                + "LEFT JOIN users a ON a.user_id = c.assigned_to "
                + "LEFT JOIN users global_admin ON global_admin.user_id = "
                + "(SELECT MIN(admin.user_id) FROM users admin WHERE admin.role = 'ADMIN') "
                + "WHERE c.user_id = ? AND EXISTS "
                + "(SELECT 1 FROM users actor WHERE actor.user_id = ? AND actor.role = 'STUDENT') "
                + "ORDER BY c.complaint_id";
        List<String[]> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, account.userId);
            ps.setInt(2, account.userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new String[] {
                            String.valueOf(rs.getInt("complaint_id")), rs.getString("title"),
                            rs.getString("priority"), rs.getString("status"),
                            rs.getString("student"), rs.getString("department_name"),
                            rs.getString("assigned"), String.valueOf(rs.getInt("age_days")) });
                }
            }
        }
        return rows;
    }

    private void requireAdmin(UserAccount admin) throws SQLException {
        if (admin == null || !"ADMIN".equals(admin.role) || admin.departmentId != null) {
            throw new IllegalArgumentException("A global administrator session is required.");
        }
        String sql = "SELECT user_id FROM users WHERE user_id = ? AND role = 'ADMIN' "
                + "AND department_id IS NULL";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, admin.userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Administrator session is invalid.");
                }
            }
        }
    }

    private void requireStaff(UserAccount staff) throws SQLException {
        if (staff == null || !"STAFF".equals(staff.role) || staff.departmentId == null) {
            throw new IllegalArgumentException("A department-assigned staff session is required.");
        }
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT user_id FROM users WHERE user_id = ? AND role = 'STAFF' AND department_id = ?")) {
            ps.setInt(1, staff.userId);
            ps.setInt(2, staff.departmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Staff session is invalid.");
            }
        }
    }

    public List<String[]> listStaffForComplaint(int complaintId, UserAccount admin) throws SQLException {
        requireAdmin(admin);
        return dao.getStaffForComplaint(complaintId, admin.userId);
    }

    public void assignComplaint(int complaintId, int staffId, UserAccount admin) throws SQLException {
        requireAdmin(admin);
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT c.status, c.department_id FROM complaints c "
                                + "WHERE c.complaint_id = ? AND EXISTS (SELECT 1 FROM users actor "
                                + "WHERE actor.user_id = ? AND actor.role = 'ADMIN' "
                                + "AND actor.department_id IS NULL) FOR UPDATE")) {
                    ps.setInt(1, complaintId);
                    ps.setInt(2, admin.userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Complaint does not exist.");
                        }
                        String status = rs.getString("status");
                        if ("RESOLVED".equals(status) || "REJECTED".equals(status)) {
                            throw new IllegalArgumentException("Closed complaints cannot be assigned.");
                        }
                        int complaintDepartmentId = rs.getInt("department_id");
                        try (PreparedStatement staffQuery = con.prepareStatement(
                                "SELECT user_id FROM users WHERE user_id = ? AND role = 'STAFF' "
                                        + "AND department_id = ?")) {
                            staffQuery.setInt(1, staffId);
                            staffQuery.setInt(2, complaintDepartmentId);
                            try (ResultSet staffResult = staffQuery.executeQuery()) {
                                if (!staffResult.next()) {
                                    throw new IllegalArgumentException(
                                            "Choose a staff member from the complaint's department.");
                                }
                            }
                        }
                        try (PreparedStatement update = con.prepareStatement(
                                "UPDATE complaints SET assigned_to = ? WHERE complaint_id = ? "
                                        + "AND department_id = ? AND EXISTS (SELECT 1 FROM users actor "
                                        + "WHERE actor.user_id = ? AND actor.role = 'ADMIN' "
                                        + "AND actor.department_id IS NULL)")) {
                            update.setInt(1, staffId);
                            update.setInt(2, complaintId);
                            update.setInt(3, complaintDepartmentId);
                            update.setInt(4, admin.userId);
                            update.executeUpdate();
                        }
                    }
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    public ComplaintDetails getComplaintDetails(int complaintId, UserAccount account) throws SQLException {
        if (account == null) throw new IllegalArgumentException("Sign in is required.");
        String accessFilter;
        if ("ADMIN".equals(account.role)) {
            requireAdmin(account);
            accessFilter = "AND EXISTS (SELECT 1 FROM users actor WHERE actor.user_id = ? "
                    + "AND actor.role = 'ADMIN' AND actor.department_id IS NULL) ";
        } else if ("STAFF".equals(account.role)) {
            requireStaff(account);
            accessFilter = "AND c.assigned_to = ? AND EXISTS (SELECT 1 FROM users actor "
                    + "WHERE actor.user_id = ? AND actor.role = 'STAFF' "
                    + "AND actor.department_id = c.department_id) ";
        } else if ("STUDENT".equals(account.role)) {
            accessFilter = "AND c.user_id = ? AND EXISTS "
                    + "(SELECT 1 FROM users student WHERE student.user_id = ? AND student.role = 'STUDENT') ";
        } else {
            throw new IllegalArgumentException("This account cannot view complaint details.");
        }
        String sql = "SELECT c.complaint_id, c.title, c.description, c.location, c.priority, c.status, "
                + "u.name AS student, d.department_name, "
                + "COALESCE(a.name, CONCAT(global_admin.name, ' (Global admin)'), '-') AS assigned, c.created_at, "
                + "DATEDIFF(CURRENT_DATE, DATE(c.created_at)) AS age_days, "
                + "cf.rating AS feedback_rating, cf.feedback_comment "
                + "FROM complaints c JOIN users u ON u.user_id = c.user_id "
                + "JOIN departments d ON d.department_id = c.department_id "
                + "LEFT JOIN users a ON a.user_id = c.assigned_to "
                + "LEFT JOIN users global_admin ON global_admin.user_id = "
                + "(SELECT MIN(admin.user_id) FROM users admin WHERE admin.role = 'ADMIN') "
                + "LEFT JOIN complaint_feedback cf ON cf.complaint_id = c.complaint_id "
                + "WHERE c.complaint_id = ? " + accessFilter;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            ps.setInt(2, account.userId);
            if (!"ADMIN".equals(account.role)) ps.setInt(3, account.userId);
            ComplaintDetails details;
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Complaint not found or you do not have access.");
                }
                details = new ComplaintDetails(rs);
            }
            try (PreparedStatement psAttachments = con.prepareStatement(
                    "SELECT file_name, mime_type, file_data FROM complaint_attachments "
                            + "WHERE complaint_id = ? ORDER BY attachment_id")) {
                psAttachments.setInt(1, complaintId);
                try (ResultSet rs = psAttachments.executeQuery()) {
                    while (rs.next()) {
                        details.attachments.add(new Attachment(rs.getString("file_name"),
                                rs.getString("mime_type"), rs.getBytes("file_data")));
                    }
                }
            }
            return details;
        }
    }

    public void submitResolutionFeedback(int complaintId, UserAccount student, int rating, String comment)
            throws SQLException {
        validateFeedbackInput(rating, comment);
        if (student == null || !"STUDENT".equals(student.role)) {
            throw new IllegalArgumentException("Only the student who submitted the complaint can rate it.");
        }

        String normalizedComment = comment == null || comment.trim().isEmpty() ? null : comment.trim();
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                String status;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT c.status FROM complaints c JOIN users u ON u.user_id = c.user_id "
                                + "WHERE c.complaint_id = ? AND c.user_id = ? AND u.role = 'STUDENT' FOR UPDATE")) {
                    ps.setInt(1, complaintId);
                    ps.setInt(2, student.userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                    "Complaint not found or you do not have access.");
                        }
                        status = rs.getString("status");
                    }
                }
                if (!"RESOLVED".equals(status)) {
                    throw new IllegalArgumentException("You can rate a complaint only after it is resolved.");
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO complaint_feedback (complaint_id, rating, feedback_comment) "
                                + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE "
                                + "rating = ?, feedback_comment = ?, updated_at = CURRENT_TIMESTAMP")) {
                    ps.setInt(1, complaintId);
                    ps.setInt(2, rating);
                    ps.setString(3, normalizedComment);
                    ps.setInt(4, rating);
                    ps.setString(5, normalizedComment);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    static void validateFeedbackInput(int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5 stars.");
        }
        if (comment != null && comment.trim().length() > 1000) {
            throw new IllegalArgumentException("Feedback comment cannot exceed 1000 characters.");
        }
    }

    /**
     * Changes a complaint's status and writes the history row in ONE transaction.
     * Rolls back if the complaint does not exist or any statement fails.
     * Returns the previous status.
     */
    public String updateStatus(int complaintId, String newStatus, UserAccount actor, String remarks)
            throws SQLException {
        return updateStatus(complaintId, newStatus, actor, remarks, null, null, null);
    }

    public String updateStatus(int complaintId, String newStatus, UserAccount actor, String remarks,
                              String attachmentName, String attachmentMime, byte[] attachmentData)
            throws SQLException {
        if (actor == null) throw new IllegalArgumentException("Sign in is required.");
        String scope;
        if ("ADMIN".equals(actor.role)) {
            requireAdmin(actor);
            scope = "EXISTS (SELECT 1 FROM users auth WHERE auth.user_id = ? "
                    + "AND auth.role = 'ADMIN' AND auth.department_id IS NULL)";
        } else if ("STAFF".equals(actor.role)) {
            requireStaff(actor);
            scope = "c.assigned_to = ? AND EXISTS (SELECT 1 FROM users auth "
                    + "WHERE auth.user_id = ? AND auth.role = 'STAFF' "
                    + "AND auth.department_id = c.department_id)";
        } else {
            throw new IllegalArgumentException("Only administrators and assigned staff can update complaint status.");
        }
        if (!STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Status must be one of " + STATUSES);
        }
        if (attachmentData != null) {
            requireText(attachmentName, "Attachment filename", 255);
            if (attachmentData.length == 0 || attachmentData.length > 10 * 1024 * 1024) {
                throw new IllegalArgumentException("Image must be between 1 byte and 10 MB.");
            }
            if (!"image/jpeg".equals(attachmentMime) && !"image/png".equals(attachmentMime)
                    && !"image/gif".equals(attachmentMime) && !"image/bmp".equals(attachmentMime)) {
                throw new IllegalArgumentException("Only JPEG, PNG, GIF, or BMP images are supported.");
            }
        }
        final String update =
                "UPDATE complaints c SET status = ?, "
                + "resolved_at = CASE WHEN ? = 'RESOLVED' THEN CURRENT_TIMESTAMP ELSE NULL END "
                + "WHERE complaint_id = ? AND " + scope;
        final String history =
                "INSERT INTO complaint_history (complaint_id, changed_by, old_status, new_status, remarks) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                String oldStatus;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT c.status FROM complaints c WHERE c.complaint_id = ? AND " + scope + " FOR UPDATE")) {
                    ps.setInt(1, complaintId);
                    ps.setInt(2, actor.userId);
                    if ("STAFF".equals(actor.role)) ps.setInt(3, actor.userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                    "Complaint does not exist or is not assigned to your account.");
                        }
                        oldStatus = rs.getString(1);
                    }
                }
                if (oldStatus.equals(newStatus)) {
                    throw new IllegalArgumentException("Complaint is already " + newStatus + ".");
                }
                try (PreparedStatement ps = con.prepareStatement(update)) {
                    ps.setString(1, newStatus);
                    ps.setString(2, newStatus);
                    ps.setInt(3, complaintId);
                    ps.setInt(4, actor.userId);
                    if ("STAFF".equals(actor.role)) ps.setInt(5, actor.userId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(history)) {
                    ps.setInt(1, complaintId);
                    ps.setInt(2, actor.userId);
                    ps.setString(3, oldStatus);
                    ps.setString(4, newStatus);
                    ps.setString(5, remarks == null || remarks.trim().isEmpty() ? null : remarks.trim());
                    ps.executeUpdate();
                }
                if (attachmentData != null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO complaint_attachments (complaint_id, file_name, mime_type, file_data) "
                                    + "VALUES (?, ?, ?, ?)")) {
                        ps.setInt(1, complaintId);
                        ps.setString(2, attachmentName);
                        ps.setString(3, attachmentMime);
                        ps.setBytes(4, attachmentData);
                        ps.executeUpdate();
                    }
                }
                con.commit();
                return oldStatus;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Reads open complaints and their actual calendar-day age for the scheduler.
     */
    public List<SchedulingTask> loadOpenComplaintsForScheduling(UserAccount admin) throws SQLException {
        requireAdmin(admin);
        final String sql =
                "SELECT c.complaint_id, c.priority, c.title, "
                + "DATEDIFF(CURRENT_DATE, DATE(c.created_at)) AS waiting_days "
                + "FROM complaints c WHERE c.status IN ('PENDING','IN_PROGRESS') "
                + "AND EXISTS (SELECT 1 FROM users actor WHERE actor.user_id = ? "
                + "AND actor.role = 'ADMIN' AND actor.department_id IS NULL) "
                + "ORDER BY c.created_at, c.complaint_id LIMIT 50";
        List<SchedulingTask> tasks = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
             ps.setInt(1, admin.userId);
            try (ResultSet rs = ps.executeQuery()) {
                int pid = 1;
                while (rs.next()) {
                    tasks.add(new SchedulingTask(pid++, rs.getInt("complaint_id"), rs.getInt("waiting_days"),
                            rs.getString("priority"), rs.getString("title")));
                }
            }
        }
        return tasks;
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty.");
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long (max " + maxLength + ").");
        }
    }
}
