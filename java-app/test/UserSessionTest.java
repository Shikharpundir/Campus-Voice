public final class UserSessionTest {
    private UserSessionTest() { }

    public static void main(String[] args) {
        UserSession session = new UserSession();
        UserAccount admin = new UserAccount(7, "Department Admin", "ADMIN", 2, "CSE");
        session.signIn(admin);
        if (session.requireRole("ADMIN") != admin) {
            throw new AssertionError("The authenticated administrator session was not retained.");
        }
        expectRoleFailure(session, "STUDENT");
        UserAccount staff = new UserAccount(8, "CSE Staff", "STAFF", 3, "CSE");
        session.signIn(staff);
        if (session.requireRole("STAFF") != staff) {
            throw new AssertionError("The staff portal did not recognize the signed-in staff role.");
        }
        session.signOut();
        if (session.currentUser() != null) {
            throw new AssertionError("Sign out did not clear the session.");
        }
        expectRoleFailure(session, "ADMIN");
        System.out.println("User session checks passed.");
    }

    private static void expectRoleFailure(UserSession session, String role) {
        try {
            session.requireRole(role);
            throw new AssertionError("Expected session role check to fail.");
        } catch (IllegalStateException expected) {
            // Expected for a wrong role or signed-out session.
        }
    }
}
