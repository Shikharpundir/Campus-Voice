import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** JDBC data access for authentication and the core complaint workflows. */
public final class CampusVoiceDAO {

    public UserAccount validateUser(String email, String password) throws SQLException {
        if (password == null) return null;
        char[] chars = password.toCharArray();
        try {
            return validateUser(email, chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    public UserAccount validateUser(String email, char[] password) throws SQLException {
        if (email == null || email.trim().isEmpty() || password == null) return null;
        String sql = "SELECT u.user_id, u.name, u.role, u.department_id, "
                + "d.department_name, u.password_hash "
                + "FROM users u LEFT JOIN departments d ON d.department_id = u.department_id "
                + "WHERE u.email = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !PasswordUtil.verify(password, rs.getString("password_hash"))) return null;
                int departmentId = rs.getInt("department_id");
                Integer scopedDepartmentId = rs.wasNull() ? null : departmentId;
                String role = rs.getString("role");
                if (!"STUDENT".equals(role) && !"ADMIN".equals(role) && !"STAFF".equals(role)) return null;
                if (("STUDENT".equals(role) || "ADMIN".equals(role)) && scopedDepartmentId != null) {
                    throw new SQLException(role + " account must not have a department assignment.");
                }
                if ("STAFF".equals(role) && scopedDepartmentId == null) {
                    throw new SQLException("Complete first-time staff setup before signing in.");
                }
                return new UserAccount(rs.getInt("user_id"), rs.getString("name"), role,
                        scopedDepartmentId, rs.getString("department_name"));
            }
        }
    }

    /** Returns complaints for a department name; callers must obtain it from the authenticated admin. */
    public List<String[]> getComplaintsByDepartment(String dept) throws SQLException {
        if (dept == null || dept.trim().isEmpty()) {
            throw new IllegalArgumentException("Department is required.");
        }
        return queryComplaintsByDepartment(dept.trim());
    }

    public List<String[]> getComplaintsForAdmin(int adminId) throws SQLException {
        String sql = "SELECT c.complaint_id, c.title, c.priority, c.status, u.name AS student, "
                + "d.department_name, COALESCE(a.name, CONCAT(global_admin.name, ' (Global admin)'), '-') AS assigned, "
                + "DATEDIFF(CURRENT_DATE, DATE(c.created_at)) AS age_days "
                + "FROM complaints c JOIN users u ON u.user_id = c.user_id "
                + "JOIN departments d ON d.department_id = c.department_id "
                + "LEFT JOIN users a ON a.user_id = c.assigned_to "
                + "LEFT JOIN users global_admin ON global_admin.user_id = "
                + "(SELECT MIN(idle_admin.user_id) FROM users idle_admin WHERE idle_admin.role = 'ADMIN') "
                + "WHERE EXISTS (SELECT 1 FROM users actor WHERE actor.user_id = ? "
                + "AND actor.role = 'ADMIN' AND actor.department_id IS NULL) "
                + "ORDER BY c.complaint_id";
        return queryComplaintRows(sql, adminId);
    }

    public List<String[]> getComplaintsForStaff(int staffId) throws SQLException {
        String sql = "SELECT c.complaint_id, c.title, c.priority, c.status, u.name AS student, "
                + "d.department_name, COALESCE(a.name, '-') AS assigned, "
                + "DATEDIFF(CURRENT_DATE, DATE(c.created_at)) AS age_days "
                + "FROM complaints c JOIN users u ON u.user_id = c.user_id "
                + "JOIN departments d ON d.department_id = c.department_id "
                + "JOIN users a ON a.user_id = c.assigned_to "
                + "WHERE c.assigned_to = ? AND a.role = 'STAFF' AND a.department_id = c.department_id "
                + "ORDER BY c.complaint_id";
        return queryComplaintRows(sql, staffId);
    }

    private List<String[]> queryComplaintRows(String sql, int userId) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
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

    public List<String[]> getStaffForComplaint(int complaintId, int adminId) throws SQLException {
        String sql = "SELECT staff.user_id, staff.name, staff.email, d.department_name "
                + "FROM complaints c JOIN departments d ON d.department_id = c.department_id "
                + "JOIN users staff ON staff.role = 'STAFF' AND staff.department_id = c.department_id "
                + "WHERE c.complaint_id = ? AND EXISTS (SELECT 1 FROM users actor "
                + "WHERE actor.user_id = ? AND actor.role = 'ADMIN' AND actor.department_id IS NULL) "
                + "ORDER BY staff.name";
        List<String[]> staff = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            ps.setInt(2, adminId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    staff.add(new String[] {String.valueOf(rs.getInt("user_id")), rs.getString("name"),
                            rs.getString("email"), rs.getString("department_name")});
                }
            }
        }
        return staff;
    }

    private List<String[]> queryComplaintsByDepartment(String dept) throws SQLException {
        String sql = "SELECT c.complaint_id, c.title, c.priority, c.status, u.name AS student, "
                + "d.department_name, COALESCE(a.name, CONCAT(global_admin.name, ' (Global admin)'), '-') AS assigned, "
                + "DATEDIFF(CURRENT_DATE, DATE(c.created_at)) AS age_days "
                + "FROM complaints c "
                + "JOIN users u ON u.user_id = c.user_id "
                + "JOIN departments d ON d.department_id = c.department_id "
                + "LEFT JOIN users a ON a.user_id = c.assigned_to "
                + "LEFT JOIN users global_admin ON global_admin.user_id = "
                + "(SELECT MIN(admin.user_id) FROM users admin WHERE admin.role = 'ADMIN') "
                + "WHERE d.department_name = ? "
                + "ORDER BY c.complaint_id";
        List<String[]> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
             ps.setString(1, dept.trim());
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

    /** Creates a complaint and its initial history entry atomically. */
    public int createComplaint(Complaint complaint) throws SQLException {
        if (complaint == null) throw new IllegalArgumentException("Complaint is required.");
        requireText(complaint.title, "Title", 150);
        requireText(complaint.description, "Description", 5000);
        requireText(complaint.location, "Location", 150);
        if (!ComplaintService.PRIORITIES.contains(complaint.priority)) {
            throw new IllegalArgumentException("Priority must be one of " + ComplaintService.PRIORITIES);
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT role FROM users WHERE user_id = ?")) {
                    ps.setInt(1, complaint.userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || !"STUDENT".equals(rs.getString("role"))) {
                            throw new IllegalArgumentException("Only a student can submit a complaint.");
                        }
                    }
                }

                int complaintId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO complaints (user_id, department_id, title, description, location, priority) "
                                + "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, complaint.userId);
                    ps.setInt(2, complaint.departmentId);
                    ps.setString(3, complaint.title.trim());
                    ps.setString(4, complaint.description.trim());
                    ps.setString(5, complaint.location.trim());
                    ps.setString(6, complaint.priority);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No complaint id was generated.");
                        complaintId = keys.getInt(1);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO complaint_history "
                                + "(complaint_id, changed_by, old_status, new_status, remarks) "
                                + "VALUES (?, ?, NULL, 'PENDING', 'Complaint submitted')")) {
                    ps.setInt(1, complaintId);
                    ps.setInt(2, complaint.userId);
                    ps.executeUpdate();
                }
                con.commit();
                return complaintId;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    public int createStaffAccount(int adminId, String name, String email)
            throws SQLException {
        String sql = "INSERT INTO users (name, email, password_hash, role, department_id) "
                + "SELECT ?, ?, ?, 'STAFF', NULL FROM users actor "
                + "WHERE actor.user_id = ? AND actor.role = 'ADMIN' AND actor.department_id IS NULL";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.setString(2, email.trim().toLowerCase());
            ps.setNull(3, java.sql.Types.VARCHAR);
            ps.setInt(4, adminId);
            if (ps.executeUpdate() != 1) {
                throw new IllegalArgumentException("Only a global administrator can create staff accounts.");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No staff user id was generated.");
                return keys.getInt(1);
            }
        }
    }

    public List<String[]> listStaffAccounts(int adminId) throws SQLException {
        String sql = "SELECT staff.name, staff.email, "
                + "COALESCE(d.department_name, 'Not selected') AS department_name, "
                + "CASE WHEN staff.password_hash IS NULL THEN 'Pending setup' ELSE 'Active' END AS account_status "
                + "FROM users staff LEFT JOIN departments d ON d.department_id = staff.department_id "
                + "WHERE staff.role = 'STAFF' AND EXISTS ("
                + "SELECT 1 FROM users actor WHERE actor.user_id = ? "
                + "AND actor.role = 'ADMIN' AND actor.department_id IS NULL) "
                + "ORDER BY staff.name, staff.email";
        List<String[]> staffAccounts = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, adminId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    staffAccounts.add(new String[] {
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("department_name"),
                            rs.getString("account_status")
                    });
                }
            }
        }
        return staffAccounts;
    }

    public UserAccount findPendingStaff(String name, String email) throws SQLException {
        String sql = "SELECT user_id, name FROM users "
                + "WHERE role = 'STAFF' AND department_id IS NULL AND password_hash IS NULL "
                + "AND LOWER(TRIM(name)) = ? AND LOWER(TRIM(email)) = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name.trim().toLowerCase());
            ps.setString(2, email.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new UserAccount(rs.getInt("user_id"), rs.getString("name"), "STAFF");
            }
        }
    }

    public UserAccount activateStaffAccount(int staffId, String expectedName, String expectedEmail,
                                            char[] password, int departmentId)
            throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                String name;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT name, department_id FROM users WHERE user_id = ? AND role = 'STAFF' "
                                + "AND department_id IS NULL AND password_hash IS NULL "
                                + "AND LOWER(TRIM(name)) = ? AND LOWER(TRIM(email)) = ? FOR UPDATE")) {
                    ps.setInt(1, staffId);
                    ps.setString(2, expectedName.trim().toLowerCase());
                    ps.setString(3, expectedEmail.trim().toLowerCase());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                    "Name and email do not match an unclaimed staff account.");
                        }
                        name = rs.getString("name");
                    }
                }

                String departmentName;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT department_name FROM departments WHERE department_id = ?")) {
                    ps.setInt(1, departmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Staff department was not found.");
                        departmentName = rs.getString("department_name");
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE users SET department_id = ?, password_hash = ? "
                                + "WHERE user_id = ? AND role = 'STAFF' "
                                + "AND department_id IS NULL AND password_hash IS NULL")) {
                    ps.setInt(1, departmentId);
                    ps.setString(2, PasswordUtil.hash(password));
                    ps.setInt(3, staffId);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalArgumentException("Staff account setup could not be completed.");
                    }
                }
                con.commit();
                return new UserAccount(staffId, name, "STAFF", departmentId, departmentName);
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
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
