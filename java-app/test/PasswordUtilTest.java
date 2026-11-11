public final class PasswordUtilTest {
    private PasswordUtilTest() { }

    public static void main(String[] args) {
        char[] password = "correct-horse-battery".toCharArray();
        String encoded = PasswordUtil.hash(password);
        if (encoded.contains("correct-horse-battery")) {
            throw new AssertionError("Password hash contains the original password.");
        }
        if (!PasswordUtil.verify(password, encoded)) {
            throw new AssertionError("Correct password was rejected.");
        }
        if (PasswordUtil.verify("incorrect-password".toCharArray(), encoded)) {
            throw new AssertionError("Incorrect password was accepted.");
        }
        if (PasswordUtil.verify(password, "dummyhash_sample")) {
            throw new AssertionError("A sample placeholder hash was accepted.");
        }
        System.out.println("PasswordUtil tests passed.");
    }
}
