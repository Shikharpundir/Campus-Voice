/** Authenticated account details used by the console menus. */
public final class UserAccount {
    public final int userId;
    public final String name;
    public final String role;
    public final Integer departmentId;
    public final String departmentName;

    public UserAccount(int userId, String name, String role) {
        this(userId, name, role, null, null);
    }

    public UserAccount(int userId, String name, String role, Integer departmentId, String departmentName) {
        this.userId = userId;
        this.name = name;
        this.role = role;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
    }
}
