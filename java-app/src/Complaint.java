/** Complaint input accepted by the data access layer. */
public final class Complaint {
    public final int userId;
    public final int departmentId;
    public final String title;
    public final String description;
    public final String location;
    public final String priority;

    public Complaint(int userId, int departmentId, String title, String description,
                     String location, String priority) {
        this.userId = userId;
        this.departmentId = departmentId;
        this.title = title;
        this.description = description;
        this.location = location;
        this.priority = priority;
    }
}
