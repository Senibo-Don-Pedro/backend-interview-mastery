# Java Optionals — Full Notes

## The Problem This Solves

Before `Optional` existed, a method that might not find something just returned `null`:

```java
public User findByUsername(String username) {
    // ... search logic ...
    return null;  // nothing found
}

User user = findByUsername("temi");
System.out.println(user.getName());  // 💥 NullPointerException
```

The caller has no signal, at the type level, that this method might return nothing. Nothing in the method signature warns you. You only find out at runtime, when it crashes — often far from where the actual bug is (the crash happens at `user.getName()`, not at the `findByUsername()` call that actually returned null).

`Optional<T>` fixes this by making "might be empty" part of the **type itself**. A method returning `Optional<User>` is explicitly telling every caller: "this might not have a value — you are required to think about that case before you can get at the value inside."

```java
public Optional<User> findByUsername(String username) {
    // ... search logic ...
    return Optional.empty();  // explicitly "nothing found", not a silent trap
}
```

Think of `Optional` as a labeled box: either it has a value inside labeled "present," or it's an empty box labeled "empty." You're never allowed to just reach in blindly — you have to ask the box first.

---

## Creating an Optional

```java
Optional.of("Temi")          // wraps a value you're GUARANTEEING is not null
                              // throws NullPointerException immediately if you pass null in — fails loud and fast

Optional.ofNullable(value)   // wraps a value that MIGHT be null
                              // if value is null, produces Optional.empty() silently — no exception
                              // use this for anything coming from an uncertain source (DB, external API, user input)

Optional.empty()             // explicitly represents "nothing", used as a return value
                              // e.g., a repository method deliberately signaling "not found"
```

**Rule of thumb:** if you're 100% sure the value can't be null (you just constructed it yourself), use `of()`. If there's any doubt (parsing user input, calling `repository.findById()`, reading a nullable database column), use `ofNullable()`.

---

## Key Methods, In Depth

```java
isPresent()   // true if there's a value inside
isEmpty()     // true if empty — Java 11+. Before that, you had to write !isPresent()
```

### `get()` — the one you should almost never call directly

```java
Optional<User> optionalUser = findByUsername("ghost");
User user = optionalUser.get();  // throws NoSuchElementException if empty
```

Calling `get()` blindly reintroduces the exact problem `Optional` was created to solve — you're back to "hope it's not empty and crash if it is," just with a different exception name. `get()` exists mainly as a low-level building block for other methods; production code should almost never call it directly.

### `orElse()` vs `orElseGet()` — the classic interview trap

```java
// orElse — ALWAYS evaluates its argument, even when the Optional has a value
User user = optionalUser.orElse(loadDefaultUserFromDatabase());

// orElseGet — LAZY. Only calls the lambda if the Optional is actually empty
User user = optionalUser.orElseGet(() -> loadDefaultUserFromDatabase());
```

Here's the trap in a concrete, costly example:

```java
public User findUser(String username) {
    return userRepository.findByUsername(username)
        .orElse(createGuestUserInDatabase());  // ⚠️ THIS RUNS EVERY SINGLE TIME
}
```

`createGuestUserInDatabase()` is a Java method call — Java evaluates method arguments *before* passing them in, regardless of whether `orElse` ends up using the result or not. So even when `findByUsername` successfully finds a real user, you've still paid the cost of hitting the database again to create a guest user you're about to throw away.

```java
public User findUser(String username) {
    return userRepository.findByUsername(username)
        .orElseGet(() -> createGuestUserInDatabase());  // ✅ only runs if truly empty
}
```

`orElseGet()` takes a `Supplier<T>` (a lambda with no arguments) instead of a plain value — that lambda is only *invoked* if the Optional is empty. This is the single most commonly cited Optional interview gotcha, because it looks harmless in code review and only shows up as a real performance issue under load.

### `orElseThrow()`

```java
User user = userRepository.findByUsername(username)
    .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
```

Same laziness benefit as `orElseGet()` — the exception is only constructed if the Optional is actually empty. This is the standard pattern for turning "not found" into a clean, typed exception instead of a raw NPE.

### `map()` — safe transformation, no manual null-checking

```java
Optional<User> optionalUser = userRepository.findById(id);

Optional<String> upperName = optionalUser.map(u -> u.getUsername().toUpperCase());
// if optionalUser was empty, map() does NOTHING and returns Optional.empty()
// if it had a value, the lambda runs and the result is wrapped in a NEW Optional
```

Without `Optional`, the same logic requires manual null-checking:

```java
// the old, ugly way
User user = userRepository.findById(id);
String upperName = null;
if (user != null) {
    upperName = user.getUsername().toUpperCase();
}
```

`map()` collapses that entire null-check into one line, and — critically — it **never throws** if the Optional is empty. It just silently produces another empty Optional, letting you keep chaining safely.

### `flatMap()` — like `map()`, but for lambdas that themselves return an Optional

```java
// Imagine getManager() ALSO returns an Optional<User>, because not every user has one
Optional<User> optionalUser = userRepository.findById(id);

// WRONG — using map() here creates a nested Optional<Optional<User>>
Optional<Optional<User>> nested = optionalUser.map(u -> u.getManager());

// RIGHT — flatMap() flattens the nesting automatically
Optional<User> manager = optionalUser.flatMap(u -> u.getManager());
```

Use `map()` when your transformation returns a plain value. Use `flatMap()` when your transformation itself returns an `Optional`.

---

## The Real-World Pattern (Very Common in Spring)

```java
public String getUppercaseUsername(Long id) {
    return userRepository.findById(id)
        .map(u -> u.getUsername().toUpperCase())
        .orElseThrow(() -> new UserNotFoundException("User not found"));
}
```

Read this left to right as a sentence: "find the user by id, if found transform it to an uppercase username, if not found throw a clean typed exception." No null-checks anywhere, no risk of an NPE slipping through, and the method signature is honest about what can go wrong.

---

## Interview Gotchas

- **`orElse` always evaluates its argument, `orElseGet` doesn't.** This is the #1 Optional question. Always prefer `orElseGet`/`orElseThrow` when the fallback involves any real work (DB call, object construction, expensive computation).
- **`isEmpty()` is Java 11+.** On older codebases you'll see `!isPresent()` instead — same meaning, older syntax.
- **Never call `get()` blindly** — it throws `NoSuchElementException` on an empty Optional, which is exactly the crash-at-runtime problem Optional exists to prevent.
- **`Optional.of(null)` throws immediately** (fails fast) — `Optional.ofNullable(null)` returns `Optional.empty()` silently (fails safe). Know which one you're using and why.

## Where NOT to Use Optional — and Why

```java
// ❌ Method parameter
public void updateUser(Optional<String> newName) { ... }
// Forces every caller to wrap arguments in Optional.of(...) just to call the method.
// Just use method overloading or a plain nullable parameter instead.

// ❌ Entity/class field
public class User {
    private Optional<String> middleName;  // breaks JPA (Hibernate can't map this to a column)
                                            // breaks Jackson (doesn't serialize/deserialize cleanly to JSON)
}

// ❌ Inside a collection
List<Optional<User>> users;  // just filter out the empties before building the list instead
```

**Golden rule: Optional is for return types only** — specifically, for signaling "this method might not find/produce a value" to the caller. It was never designed to be a general-purpose null-replacement everywhere in your codebase.

---

## Quick Summary

- `Optional<T>` makes "this might be empty" part of the method's return type, forcing callers to handle the empty case instead of risking a silent NPE later.
- `Optional.of()` = guaranteed non-null (throws immediately if wrong). `Optional.ofNullable()` = might be null (safely becomes empty).
- `orElse()` always evaluates its argument eagerly — dangerous for expensive fallbacks. `orElseGet()`/`orElseThrow()` are lazy — only run if actually empty. Always prefer the lazy versions for non-trivial fallbacks.
- `map()` transforms the value inside if present, does nothing if empty. `flatMap()` does the same but flattens when the transformation itself returns an Optional.
- Never use Optional as a method parameter, entity field, or inside a collection — it's designed exclusively for return types.

## Code Reference

See `OptionalsTutorial.java` (your practice file) — demonstrates `orElseGet` vs a plain default, `ifPresent`, `orElseThrow`, and `map().orElse()` chaining, all built around a `findCatByName()` method that deliberately returns empty.
