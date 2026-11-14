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

/** Role-scoped workflow for student reports about staff members. */
public final class StaffReportService {
    public static final Set<String> REASONS = new HashSet<>(Arrays.asList(
            "IGNORED_COMPLAINT", "UNNECESSARY_DELAY", "FAKE_RESOLUTION", "MISCONDUCT"));
    public static final Set<String> STATUSES = new HashSet<>(Arrays.asList(
            "SUBMITTED", "UNDER_REVIEW", "ACTION_TAKEN", "CLOSED"));

    public static final class ReportDetails {
        public final int reportId;
        public final String student;
        public final String staff;
        public final String reason;
        public final String description;
        public final String status;
        public final String adminResponse;
        public final String createdAt;
        public final Integer complaintId;
        public final String complaintTitle;
        public final ComplaintService.Attachment evidence;

        private ReportDetails(ResultSet rs, ComplaintService.Attachment evidence) throws SQLException {
            reportId = rs.getInt("report_id");
            student = rs.getString("student_name");
            staff = rs.getString("staff_name");
            reason = rs.getString("reason");
            description = rs.getString("description");
            status = rs.getString("status");
            adminResponse = rs.getString("admin_response");
            createdAt = rs.getString("created_at");
            int linkedComplaintId = rs.getInt("complaint_id");
            complaintId = rs.wasNull() ? null : linkedComplaintId;
            complaintTitle = rs.getString("complaint_title");
            this.evidence = evidence;
        }
    }

    public List<String[]> listReportableStaff(UserAccount student) throws SQLException {
        requireStudent(student);
        List<String[]> staff = new ArrayList<>();
        String sql = "SELECT s.user_id, s.name, d.department_name FROM users s "
                + "JOIN departments d ON d.department_id = s.department_id "
                + "WHERE s.role = 'STAFF' AND s.department_id IS NOT NULL "
                + "AND EXISTS (SELECT 1 FROM users actor WHERE actor.user_id = ? "
                + "AND actor.role = 'STUDENT') ORDER BY s.name";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, student.userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    staff.add(new String[] {String.valueOf(rs.getInt("user_id")),
                            rs.getString("name"), rs.getString("department_name")});
                }
            }
        }
        return staff;
    }

    public List<String[]> listStudentComplaintsForStaff(UserAccount student, int staffId) throws SQLException {
        requireStudent(student);
        List<String[]> complaints = new ArrayList<>();
        String sql = "SELECT c.complaint_id, c.title, c.status FROM complaints c "
                + "JOIN users s ON s.user_id = c.assigned_to AND s.role = 'STAFF' "
                + "WHERE c.user_id = ? AND c.assigned_to = ? AND EXISTS "
                + "(SELECT 1 FROM users actor WHERE actor.user_id = ? AND actor.role = 'STUDENT') "
                + "ORDER BY c.complaint_id DESC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, student.userId);
            ps.setInt(2, staffId);
            ps.setInt(3, student.userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    complaints.add(new String[] {String.valueOf(rs.getInt("complaint_id")),
                            rs.getString("title"), rs.getString("status")});
                }
            }
        }
        return complaints;
    }

    public int submitReport(UserAccount student, int staffId, Integer complaintId, String reason,
                            String description, String evidenceName, String evidenceMime, byte[] evidenceData)
            throws SQLException {
        validateReportInput(reason, description, evidenceName, evidenceMime, evidenceData);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                if (!isStudent(con, student)) {
                    throw new IllegalArgumentException("A valid student session is required.");
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT user_id FROM users WHERE user_id = ? AND role = 'STAFF' "
                                + "AND department_id IS NOT NULL")) {
                    ps.setInt(1, staffId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Select an active staff member.");
                    }
                }
                if (complaintId != null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT c.complaint_id FROM complaints c JOIN users s "
                                    + "ON s.user_id = c.assigned_to AND s.role = 'STAFF' "
                                    + "WHERE c.complaint_id = ? AND c.user_id = ? AND c.assigned_to = ? "
                                    + "FOR UPDATE")) {
                        ps.setInt(1, complaintId);
                        ps.setInt(2, student.userId);
                        ps.setInt(3, staffId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new IllegalArgumentException(
                                        "The linked complaint must belong to you and be assigned to this staff member.");
                            }
                        }
                    }
                }
                int reportId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO staff_reports (student_id, staff_id, complaint_id, reason, description) "
                                + "VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, student.userId);
                    ps.setInt(2, staffId);
                    if (complaintId == null) ps.setNull(3, java.sql.Types.INTEGER);
                    else ps.setInt(3, complaintId);
                    ps.setString(4, reason);
                    ps.setString(5, description.trim());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No staff report id was generated.");
                        reportId = keys.getInt(1);
                    }
                }
                if (evidenceData != null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO staff_report_evidence "
                                    + "(report_id, file_name, mime_type, file_data) VALUES (?, ?, ?, ?)")) {
                        ps.setInt(1, reportId);
                        ps.setString(2, evidenceName);
                        ps.setString(3, evidenceMime);
                        ps.setBytes(4, evidenceData);
                        ps.executeUpdate();
                    }
                }
                con.commit();
                return reportId;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    public List<String[]> listOwnReports(UserAccount student) throws SQLException {
        requireStudent(student);
        return queryReportRows("WHERE r.student_id = ? AND EXISTS "
                + "(SELECT 1 FROM users actor WHERE actor.user_id = ? AND actor.role = 'STUDENT')",
                student.userId, student.userId);
    }

    public List<String[]> listReportsForAdmin(UserAccount admin) throws SQLException {
        requireAdmin(admin);
        return queryReportRows("WHERE EXISTS (SELECT 1 FROM users actor WHERE actor.user_id = ? "
                + "AND actor.role = 'ADMIN' AND actor.department_id IS NULL)", admin.userId);
    }

    private List<String[]> queryReportRows(String filter, int... actorIds) throws SQLException {
        List<String[]> reports = new ArrayList<>();
        String sql = "SELECT r.report_id, r.created_at, student.name AS student_name, "
                + "staff.name AS staff_name, r.reason, r.status, COALESCE(c.title, '-') AS complaint_title, "
                + "COALESCE(r.admin_response, '') AS admin_response "
                + "FROM staff_reports r JOIN users student ON student.user_id = r.student_id "
                + "JOIN users staff ON staff.user_id = r.staff_id "
                + "LEFT JOIN complaints c ON c.complaint_id = r.complaint_id " + filter
                + " ORDER BY r.created_at DESC, r.report_id DESC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < actorIds.length; i++) ps.setInt(i + 1, actorIds[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reports.add(new String[] {String.valueOf(rs.getInt("report_id")),
                            rs.getString("created_at"), rs.getString("student_name"),
                            rs.getString("staff_name"), rs.getString("reason"), rs.getString("status"),
                            rs.getString("complaint_title"), rs.getString("admin_response")});
                }
            }
        }
        return reports;
    }

    public ReportDetails getReportDetails(int reportId, UserAccount viewer) throws SQLException {
        if (viewer == null) throw new IllegalArgumentException("Sign in is required.");
        final String access;
        if ("ADMIN".equals(viewer.role)) {
            requireAdmin(viewer);
            access = "AND EXISTS (SELECT 1 FROM users actor WHERE actor.user_id = ? "
                    + "AND actor.role = 'ADMIN' AND actor.department_id IS NULL) ";
        } else if ("STUDENT".equals(viewer.role)) {
            requireStudent(viewer);
            access = "AND r.student_id = ? AND EXISTS (SELECT 1 FROM users actor "
                    + "WHERE actor.user_id = ? AND actor.role = 'STUDENT') ";
        } else {
            throw new IllegalArgumentException("Staff reports are visible only to the reporting student and admin.");
        }
        String sql = "SELECT r.report_id, student.name AS student_name, staff.name AS staff_name, "
                + "r.reason, r.description, r.status, r.admin_response, r.created_at, r.complaint_id, "
                + "c.title AS complaint_title, evidence.file_name AS evidence_file_name, "
                + "evidence.mime_type AS evidence_mime_type, evidence.file_data AS evidence_file_data "
                + "FROM staff_reports r "
                + "JOIN users student ON student.user_id = r.student_id "
                + "JOIN users staff ON staff.user_id = r.staff_id "
                + "LEFT JOIN complaints c ON c.complaint_id = r.complaint_id "
                + "LEFT JOIN staff_report_evidence evidence ON evidence.report_id = r.report_id "
                + "WHERE r.report_id = ? " + access;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, reportId);
            ps.setInt(2, viewer.userId);
            if ("STUDENT".equals(viewer.role)) ps.setInt(3, viewer.userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Report not found or you do not have access.");
                ComplaintService.Attachment evidence = rs.getString("evidence_file_name") == null ? null
                        : new ComplaintService.Attachment(rs.getString("evidence_file_name"),
                                rs.getString("evidence_mime_type"), rs.getBytes("evidence_file_data"));
                return new ReportDetails(rs, evidence);
            }
        }
    }

    public void updateReport(int reportId, String status, String adminResponse, UserAccount admin)
            throws SQLException {
        requireAdmin(admin);
        requireText(adminResponse, "Student update", 2000);
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("Select a valid report status.");
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE staff_reports SET status = ?, admin_response = ? WHERE report_id = ? "
                             + "AND EXISTS (SELECT 1 FROM users actor WHERE actor.user_id = ? "
                             + "AND actor.role = 'ADMIN' AND actor.department_id IS NULL)")) {
            ps.setString(1, status);
            ps.setString(2, adminResponse.trim());
            ps.setInt(3, reportId);
            ps.setInt(4, admin.userId);
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Report not found or access denied.");
        }
    }

    public void deleteReport(int reportId, UserAccount actor) throws SQLException {
        if (actor == null) throw new IllegalArgumentException("Sign in is required.");
        final String sql;
        if ("ADMIN".equals(actor.role)) {
            requireAdmin(actor);
            sql = "DELETE FROM staff_reports WHERE report_id = ? AND EXISTS "
                    + "(SELECT 1 FROM users actor WHERE actor.user_id = ? "
                    + "AND actor.role = 'ADMIN' AND actor.department_id IS NULL)";
        } else if ("STUDENT".equals(actor.role)) {
            requireStudent(actor);
            sql = "DELETE FROM staff_reports WHERE report_id = ? AND student_id = ? AND EXISTS "
                    + "(SELECT 1 FROM users actor WHERE actor.user_id = ? AND actor.role = 'STUDENT')";
        } else {
            throw new IllegalArgumentException(
                    "Only the reporting student or global administrator can delete a staff report.");
        }
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, reportId);
            ps.setInt(2, actor.userId);
            if ("STUDENT".equals(actor.role)) ps.setInt(3, actor.userId);
            if (ps.executeUpdate() != 1) {
                throw new IllegalArgumentException("Report not found or you do not have permission to delete it.");
            }
        }
    }

    public static void validateEvidence(String name, String mimeType, byte[] data) {
        if (data == null) return;
        requireText(name, "Evidence filename", 255);
        if (data.length == 0 || data.length > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Evidence image must be between 1 byte and 10 MB.");
        }
        if (!Arrays.asList("image/jpeg", "image/png", "image/gif", "image/bmp").contains(mimeType)) {
            throw new IllegalArgumentException("Evidence must be a JPEG, PNG, GIF, or BMP image.");
        }
    }

    public static void validateReportInput(String reason, String description, String evidenceName,
                                           String evidenceMime, byte[] evidenceData) {
        if (!REASONS.contains(reason)) throw new IllegalArgumentException("Select a valid report reason.");
        requireText(description, "Description", 5000);
        validateEvidence(evidenceName, evidenceMime, evidenceData);
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " is required.");
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " must be " + maxLength + " characters or fewer.");
        }
    }

    private static boolean isStudent(Connection con, UserAccount student) throws SQLException {
        if (student == null || !"STUDENT".equals(student.role)) return false;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT user_id FROM users WHERE user_id = ? AND role = 'STUDENT'")) {
            ps.setInt(1, student.userId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static void requireStudent(UserAccount student) throws SQLException {
        if (student == null || !"STUDENT".equals(student.role)) {
            throw new IllegalArgumentException("A valid student session is required.");
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            if (!isStudent(con, student)) {
                throw new IllegalArgumentException("A valid student session is required.");
            }
        }
    }

    private static void requireAdmin(UserAccount admin) throws SQLException {
        if (admin == null || !"ADMIN".equals(admin.role) || admin.departmentId != null) {
            throw new IllegalArgumentException("A global administrator session is required.");
        }
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT user_id FROM users WHERE user_id = ? AND role = 'ADMIN' "
                             + "AND department_id IS NULL")) {
            ps.setInt(1, admin.userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Administrator session is invalid.");
            }
        }
    }
}
