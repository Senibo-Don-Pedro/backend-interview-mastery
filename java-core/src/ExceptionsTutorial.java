import java.io.FileReader;
import java.io.IOException;

public class ExceptionsTutorial {

    public static void main(String[] args) {

        // ── 1. Checked Exception — must handle or code won't compile ─────────
        // IOException is checked — extends Exception directly
        try {
            FileReader file = new FileReader("data.txt");
        } catch (IOException e) {
            System.out.println("Checked exception caught: " + e.getMessage());
        }


        // ── 2. Unchecked Exception — compiles fine, crashes at runtime ────────
        // NullPointerException is unchecked — extends RuntimeException
        try {
            String name = null;
            name.length(); // crashes here
        } catch (NullPointerException e) {
            System.out.println("Unchecked exception caught: " + e.getMessage());
        }


        // ── 3. Declaring throws — let the caller deal with it ─────────────────
        // See readFile() method below — it declares throws IOException
        // instead of handling it internally
        try {
            readFile();
        } catch (IOException e) {
            System.out.println("Caller handling the checked exception: " + e.getMessage());
        }


        // ── 4. Custom unchecked exception — the Spring way ────────────────────
        // Always extend RuntimeException, never Exception
        try {
            findUser(99L);
        } catch (UserNotFoundException e) {
            System.out.println("Custom exception caught: " + e.getMessage());
        }


        // ── 5. Custom exception for banking — no try-catch needed ─────────────
        // Unchecked exceptions don't force the caller to wrap in try-catch
        // Spring catches these at the top level automatically
        withdraw(100.0, 50.0);   // fine
        withdraw(100.0, 200.0);  // throws InsufficientFundsException


    }


    // ── Declaring throws instead of catching ──────────────────────────────────
    // Passes responsibility to whoever calls this method
    private static void readFile() throws IOException {
        FileReader file = new FileReader("data.txt");
    }


    // ── Custom exception usage — simulating Spring service layer ──────────────
    private static User findUser(Long id) {
        // Simulating a user not found scenario
        if (id == 99L) {
            throw new UserNotFoundException("User not found with id: " + id);
        }
        return new User(id, "Temi");
    }


    // ── Banking example — InsufficientFundsException ──────────────────────────
    private static void withdraw(double balance, double amount) {
        if (amount > balance) {
            throw new InsufficientFundsException(
                "Cannot withdraw " + amount + ". Balance is only " + balance
            );
        }
        System.out.println("Withdrawal successful. Remaining: " + (balance - amount));
    }
}


// ── Custom Exceptions ─────────────────────────────────────────────────────────

// Always extend RuntimeException — not Exception
class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}

class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

// ── Simple User class for the example ────────────────────────────────────────
class User {
    private Long id;
    private String name;

    User(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
