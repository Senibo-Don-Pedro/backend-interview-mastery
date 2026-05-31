# 04 — Checked vs Unchecked Exceptions

## The two categories

**Checked Exception**
- Extends `Exception` directly
- Java forces you to handle it at compile time — code won't compile otherwise
- Handle with try-catch OR declare `throws` on the method
- Examples: `IOException`, `SQLException`

**Unchecked Exception**
- Extends `RuntimeException`
- Java does NOT force you to handle it — compiles fine, crashes at runtime
- Examples: `NullPointerException`, `IllegalArgumentException`, `IllegalStateException`

## How to tell which is which
Simple rule: does it extend `RuntimeException`? → Unchecked. Extends `Exception` directly? → Checked.

## Handling checked exceptions
```java
// Option A — catch it
try {
    FileReader file = new FileReader("data.txt");
} catch (IOException e) {
    System.out.println("File not found: " + e.getMessage());
}

// Option B — declare and let caller deal with it
public void readFile() throws IOException {
    FileReader file = new FileReader("data.txt");
}
```

## Why Spring prefers unchecked (RuntimeException)
Spring has a layered architecture: Controller → Service → Repository.

If a checked exception happens in the Repository, Java forces every layer above it
to declare `throws` in their method signatures — even layers that don't care about it.
This pollutes every method with exceptions they have nothing to do with.

With unchecked exceptions, the exception bubbles up automatically.
Spring catches it at the top and handles it cleanly. No pollution.

## Custom exceptions — the Spring way
Always extend RuntimeException, never Exception.

```java
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}

// Using it in a service
public User findById(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
}
```

## Interview gotchas
- `NullPointerException` is unchecked — extends `RuntimeException`
- The reason Spring uses unchecked is clean layered architecture, not convenience
- Always create custom exceptions that extend `RuntimeException` in Spring

## Quick summary
- Checked = must handle, extends Exception, compile-time enforcement
- Unchecked = optional handling, extends RuntimeException, runtime crash
- Spring prefers unchecked to avoid polluting every layer with throws declarations
- Custom Spring exceptions always extend RuntimeException

## Code practice
See: `java-core/ExceptionsTutorial.java`
