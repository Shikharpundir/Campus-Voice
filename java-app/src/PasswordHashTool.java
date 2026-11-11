import java.util.Arrays;
import java.util.Scanner;

/** Generates a password hash for creating an administrator directly in MySQL. */
public final class PasswordHashTool {
    private PasswordHashTool() { }

    public static void main(String[] args) {
        try (Scanner input = new Scanner(System.in)) {
            System.out.print("Enter administrator password (minimum 8 characters): ");
            char[] password = input.nextLine().toCharArray();
            System.out.println();
            try {
                if (password.length < 8) {
                    System.err.println("Password must contain at least 8 characters.");
                    return;
                }
                System.out.println(PasswordUtil.hash(password));
            } finally {
                Arrays.fill(password, '\0');
            }
        }
    }
}
