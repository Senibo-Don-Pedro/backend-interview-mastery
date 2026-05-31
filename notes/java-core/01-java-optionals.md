# Java Optionals

**What is it?** A box that either holds a value or holds nothing. Replaces `null` returns to prevent NullPointerException.

---

## Creating
```java
Optional.of("Temi")          // value guaranteed not null
Optional.ofNullable(value)   // value might be null — use this most
Optional.empty()             // explicitly returning nothing
```

## Key methods
| Method | One line |
|---|---|
| `isPresent()` | true if has value |
| `isEmpty()` | true if empty — Java 11+ only |
| `get()` | gets value — UNSAFE, avoid it |
| `orElse(val)` | fallback — always runs even if value exists |
| `orElseGet(() -> val)` | fallback — lazy, only runs if empty. Use this |
| `orElseThrow(() -> ex)` | throws if empty |
| `map(u -> u.getName())` | transform value — silent if empty, no crash |
| `flatMap(...)` | like map, but when lambda itself returns Optional |

## Real usage pattern (very common in Spring)
```java
userRepository.findById(id)
    .map(u -> u.getUsername().toUpperCase())
    .orElseThrow(() -> new UserNotFoundException("User not found"));
```

## Interview gotchas
- `orElse` always evaluates fallback — even if value exists. Dangerous if fallback is a DB call
- `orElseGet` is lazy — only runs when empty. Always prefer this for expensive fallbacks
- `isEmpty()` is Java 11+ — before that use `!isPresent()`
- Never call `get()` blindly — throws `NoSuchElementException` if empty

## Never use Optional as:
- ❌ Method parameter
- ❌ Entity/class field (breaks JPA + Jackson)
- ❌ Inside a collection

**Golden rule: Optional is for return types only.**

## Code practice
See: `java-core/Optionals.java`