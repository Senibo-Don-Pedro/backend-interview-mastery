# Checked vs Unchecked Exceptions — Full Notes

## The Problem This Solves

Things go wrong at runtime constantly — a file doesn't exist, a network call times out, a database constraint gets violated, a user passes in garbage input. Java needs a mechanism to signal "something went wrong here" and let it propagate to code that knows how to handle it, possibly several layers up the call stack from where it actually happened. **Exceptions** are that mechanism. But Java splits exceptions into two categories with genuinely different compile-time behavior, and knowing which category to reach for — and why — is a real architectural decision, not just syntax trivia.

---

## The Two Categories

### Checked Exceptions

```java
public class SomeCheckedException extends Exception { ... }
```

- Extends `Exception` **directly**.
- The Java compiler **forces** you to deal with it — your code will not compile unless you either catch it or explicitly declare that your method might throw it.
- Represents conditions that are, in principle, recoverable and expected to sometimes happen as part of normal operation — like a file not existing, or a network resource being unreachable.
- Examples: `IOException`, `SQLException`.

### Unchecked Exceptions

```java
public class SomeUncheckedException extends RuntimeException { ... }
```

- Extends `RuntimeException`.
- The compiler does **not** force you to handle it in any way. Code compiles fine even if the exception is never caught anywhere — it just crashes at runtime if it's thrown and nothing catches it.
- Represents programming errors or conditions considered exceptional enough that forcing every caller to handle them everywhere would be excessive — like a null reference being dereferenced, or an illegal argument being passed in.
- Examples: `NullPointerException`, `IllegalArgumentException`, `IllegalStateException`.

### The simple test

**Does it extend `RuntimeException`?** → Unchecked. **Does it extend `Exception` directly (and not through `RuntimeException`)?** → Checked. (Both ultimately extend `Throwable`, but that ancestry split is what determines checked-vs-unchecked, not `Throwable` itself.)

---

## Handling Checked Exceptions — Your Two Options

Because the compiler enforces handling, you have exactly two escape routes:

**Option A — catch it right where it happens:**

```java
try {
    FileReader file = new FileReader("data.txt");
} catch (IOException e) {
    System.out.println("File not found: " + e.getMessage());
}
```

**Option B — declare it and pass the responsibility up to whoever calls your method:**

```java
public void readFile() throws IOException {
    FileReader file = new FileReader("data.txt");
    // no try-catch here — this method is explicitly saying
    // "I might throw IOException, and it's YOUR job to deal with it"
}

// now the caller is forced to handle it:
public void useReadFile() {
    try {
        readFile();
    } catch (IOException e) {
        System.out.println("Caller handling the checked exception: " + e.getMessage());
    }
}
```

Notice that declaring `throws IOException` doesn't make the exception go away — it just relocates the compiler's requirement to handle it, one level up the call stack.

---

## Why Spring Prefers Unchecked Exceptions — The Real Architectural Reason

This is the part that's easy to memorize as a fact ("Spring likes RuntimeException") without actually understanding *why*. Here's the mechanism:

Spring applications are typically layered:

```
Controller → Service → Repository
```

Imagine the Repository layer throws a **checked** exception when something goes wrong at the database level:

```java
public class WalletRepository {
    public Wallet findById(UUID id) throws SQLException {   // checked — forces this signature everywhere upstream
        // ...
    }
}
```

Because `SQLException` is checked, **every single method between the Repository and wherever the exception is finally handled must also declare `throws SQLException`** — even layers that have absolutely nothing to do with SQL and don't know or care what a `SQLException` even is:

```java
public class WalletService {
    public Wallet getWallet(UUID id) throws SQLException {   // forced to declare this,
        return walletRepository.findById(id);                 // purely because it CALLS something that throws it
    }
}

public class WalletController {
    public ResponseEntity<?> getWallet(UUID id) throws SQLException {   // forced AGAIN,
        return ResponseEntity.ok(walletService.getWallet(id));           // even though a Controller
    }                                                                    // shouldn't need to know SQL exists
}
```

This is called **exception signature pollution** — every method along the call chain gets contaminated with a `throws` clause about an implementation detail (SQL) that most of those layers have no business knowing about. It also makes refactoring painful: swap out the database library later, and you may need to touch every method signature in the chain.

**With unchecked exceptions**, none of this is required:

```java
public class WalletRepository {
    public Wallet findById(UUID id) {   // no throws clause needed anywhere
        // if something goes wrong, throw an unchecked exception —
        // it bubbles up automatically through every layer without any signature changes
        throw new WalletNotFoundException("Wallet not found: " + id);
    }
}

public class WalletService {
    public Wallet getWallet(UUID id) {   // clean — no exception pollution
        return walletRepository.findById(id);
    }
}

public class WalletController {
    public ResponseEntity<?> getWallet(UUID id) {   // clean, and Spring's global
        return ResponseEntity.ok(walletService.getWallet(id));  // exception handler (@ExceptionHandler /
    }                                                             // @ControllerAdvice) catches it centrally
}
```

The exception propagates up through every layer automatically — no method signature needs to know or declare anything about it — until Spring's centralized exception handling (typically a `@ControllerAdvice` class) catches it once, at the top, and converts it into a proper HTTP response. **This is the actual reason Spring favors `RuntimeException`: it keeps every layer's method signature focused only on its own responsibility**, not a convenience shortcut to avoid typing `throws`.

---

## Custom Exceptions — The Spring Way

```java
// Always extend RuntimeException, never Exception, for custom exceptions in a Spring codebase
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
```

Using it in a service, paired naturally with `Optional`'s `orElseThrow()`:

```java
public User findById(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
}
```

And in a banking context:

```java
private static void withdraw(double balance, double amount) {
    if (amount > balance) {
        throw new InsufficientFundsException(
            "Cannot withdraw " + amount + ". Balance is only " + balance
        );
    }
    System.out.println("Withdrawal successful. Remaining: " + (balance - amount));
}
```

Notice there's no `try-catch` needed anywhere around `withdraw(100.0, 200.0)` at the call site if you don't want one — because it's unchecked, the exception bubbles up automatically. If nothing catches it, Spring's centralized exception handling (or, in a plain program, the JVM itself) deals with it at the top level.

---

## Interview Gotchas

- **`NullPointerException` is unchecked** — it extends `RuntimeException`. This trips people up because NPEs feel like they "should" be forced-handled given how common and dangerous they are, but Java's design philosophy treats them as programmer errors, not expected/recoverable conditions.
- **The reason Spring prefers unchecked exceptions is clean layered architecture — not laziness or convenience.** Forcing checked exceptions through every layer pollutes method signatures with implementation details that unrelated layers shouldn't need to know about.
- **Always make custom exceptions in a Spring codebase extend `RuntimeException`.** This is a near-universal convention in Spring codebases, precisely because of the signature-pollution problem above.
- **Declaring `throws X` doesn't handle the exception — it relocates responsibility for handling it to the caller.** It's not a fix, it's a hand-off.

## Quick Summary

- **Checked** = extends `Exception` directly, compiler forces handling (catch or declare `throws`), represents expected/recoverable conditions (`IOException`, `SQLException`).
- **Unchecked** = extends `RuntimeException`, no compiler enforcement, compiles fine even unhandled, crashes at runtime if uncaught (`NullPointerException`, `IllegalArgumentException`).
- **Spring prefers unchecked** because checked exceptions in a layered architecture force every intermediate layer's method signature to declare `throws`, even layers with no relationship to the actual failure — a problem called exception signature pollution.
- **Custom exceptions in Spring should always extend `RuntimeException`**, letting them bubble up automatically to be caught centrally (typically via `@ControllerAdvice`), rather than needing explicit handling at every layer.

## Code Reference

See `ExceptionsTutorial.java` (your practice file) — demonstrates catching a checked `IOException`, an unchecked `NullPointerException` crashing at the point of use, declaring `throws IOException` and having the caller handle it, and two custom `RuntimeException` subclasses (`UserNotFoundException`, `InsufficientFundsException`) used exactly the way Spring service layers use them.
