/** In-memory signed-in session for the Swing client. */
public final class UserSession {
    private UserAccount account;

    public void signIn(UserAccount authenticatedAccount) {
        if (authenticatedAccount == null) throw new IllegalArgumentException("Authenticated account is required.");
        account = authenticatedAccount;
    }

    public UserAccount currentUser() {
        return account;
    }

    public UserAccount requireRole(String role) {
        if (account == null) throw new IllegalStateException("Sign in is required.");
        if (!role.equals(account.role)) throw new IllegalStateException("This action requires the " + role + " role.");
        return account;
    }

    public void signOut() {
        account = null;
    }
}
