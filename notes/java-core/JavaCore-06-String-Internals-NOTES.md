# String Internals — Full Notes

## The Problem This Solves

Strings are used everywhere — account numbers, usernames, JSON payloads, log messages. Java made a deliberate design decision that `String` objects are **immutable**: once created, a `String`'s content can never be changed. Understanding why this decision was made, and what it implies for how Strings behave in memory, explains a whole cluster of Java behaviors that otherwise seem confusing or arbitrary — why `==` sometimes "works" on Strings and sometimes doesn't, why concatenating in a loop is a performance trap, and what `intern()` is even for.

---

## Immutability — the Core Fact

Every method you call on a `String` that looks like it "modifies" it actually returns a **brand-new** `String` object. The original is completely untouched.

```java
String name = "Temi";
name.toUpperCase();           // this creates a NEW String "TEMI" — and then discards it, since it's not captured
System.out.println(name);     // "Temi" — completely unchanged

String upper = name.toUpperCase();  // NOW we capture the new String in a variable
System.out.println(upper);          // "TEMI"
```

This is a genuinely common beginner trap: calling `name.toUpperCase()` on its own line and expecting `name` itself to have changed. It never will — `String` has no methods that mutate its own content, full stop.

### Why immutability matters — the real reasons, not just "it's a JDK design choice"

- **Security.** Sensitive strings — account numbers, tokens, passwords held briefly in memory — can't be silently altered mid-use by some other piece of code holding the same reference. If `String` were mutable, one part of a program could hand a `String` to another part, and that second part could quietly modify it out from under the first, corrupting data mid-transaction with no warning.
- **Thread safety.** Multiple threads can freely share the same `String` object with zero risk of one thread's changes corrupting another thread's read, because there simply is no way to change it. This eliminates an entire category of concurrency bugs for free.
- **Enables the String Pool** (explained next) — it's only safe for the JVM to silently reuse the same object across many variables if that object can never be changed later by any one of them. If Strings were mutable, pooling them would be dangerous — modifying one variable's "string" would invisibly corrupt every other variable secretly sharing that same pooled object.

---

## The String Pool

The JVM maintains a special region of memory — the **String pool** — that caches `String` objects created from literals. When you write a String literal in your code, Java checks the pool first: if an identical value already exists there, Java reuses that exact same object instead of allocating a new one.

```java
String a = "Temi";
String b = "Temi";

a == b;   // true — both variables point to the SAME object in the pool
```

This is purely a memory-saving optimization, made safe only because Strings are immutable — reusing the same object across many variables would be catastrophic if any one of those variables could later mutate it and corrupt it for everyone else referencing it.

---

## `new String()` — Deliberately Bypassing the Pool

```java
String a = "Temi";              // checks the pool, reuses the existing object
String b = new String("Temi");  // explicitly forces a BRAND NEW object in regular heap memory, ignoring the pool entirely

a == b;         // false — two genuinely different objects in memory
a.equals(b);    // true — same content, just stored in two separate objects
```

`new String(...)` is one of the few cases in Java where you're explicitly telling the JVM "no, don't optimize this — give me a fresh, distinct object even if an identical one already exists in the pool." This is rarely something you actually want to do deliberately in real code, but it's a common interview example precisely because it demonstrates the pool/no-pool distinction so clearly.

---

## Why `==` Is Unreliable for Strings — and Why You Should Never Use It

Whether two Strings with identical content return `true` or `false` for `==` depends entirely on the (often invisible, compiler-dependent) mechanism used to create them — literal vs `new String()`, string concatenation at compile-time vs runtime, deserialization from JSON, reading from a file, etc. This is exactly why relying on `==` for String comparison is fragile: the same logical content can silently behave differently depending on code you didn't write or can't easily see (like whatever internal library constructed the String you're comparing against).

```java
String s1 = "Hello";
String s2 = "Hello";
String s3 = new String("Hello");

s1 == s2;         // true — both literals, both pulled from the pool
s1 == s3;         // false — s3 was forced into a separate object via new String()
s1.equals(s3);    // true — ALWAYS reliable, regardless of how each String was constructed
```

**Rule: always use `.equals()` to compare String content. Never `==`.** `==` should be reserved for cases where you genuinely want to know "are these the exact same object in memory" — which, for Strings specifically, is almost never what you actually mean to ask.

---

## `intern()` — Manually Pulling a String Into the Pool

```java
String a = "Temi";                       // in the pool already
String b = new String("Temi").intern();  // forces this specific object's content to be looked up
                                          // in the pool, and returns the pooled reference instead

a == b;   // true — b now points to the exact same pooled object as a
```

`intern()` is rarely used directly in day-to-day application code, but it's a fairly common interview question, because it demonstrates that you understand the pool isn't some magic that only applies to literals — you can manually opt any String into it.

---

## Performance Trap — String Concatenation Inside Loops

Because every String is immutable, every `+` concatenation on Strings doesn't modify anything — it silently creates a **brand new** String object, copying the entire combined content into it, and discards the previous intermediate result.

```java
// BAD — creates a NEW String object on every single iteration
String result = "";
for (Transaction t : transactions) {
    result = result + t.getId();  // if there are 10,000 transactions, this creates
                                    // 10,000 separate, increasingly large String objects,
                                    // most of which are immediately thrown away as garbage
}
```

Trace through what actually happens for just 3 iterations to make this concrete:

```
result = ""                      → object A: ""
result = result + t1.getId()     → object B: "id1"           (A discarded)
result = result + t2.getId()     → object C: "id1id2"        (B discarded)
result = result + t3.getId()     → object D: "id1id2id3"     (C discarded)
```

Every intermediate object gets thrown away almost immediately, wasting both the CPU time to build them and creating garbage for the JVM's garbage collector to eventually clean up. At 10,000 iterations, this is genuinely wasteful — not a micro-optimization nitpick, but a real, measurable performance problem.

```java
// GOOD — StringBuilder is a genuinely MUTABLE object, purpose-built for exactly this
StringBuilder sb = new StringBuilder();
for (Transaction t : transactions) {
    sb.append(t.getId());   // modifies the SAME internal buffer each time — no new object per iteration
}
String result = sb.toString();  // only ONE final String object is created, at the very end
```

`StringBuilder` exists specifically as the mutable escape hatch for scenarios like this — internally, it maintains a resizable character buffer (conceptually similar to how `ArrayList` maintains a resizable array), and `append()` just writes into that buffer directly, without discarding and recreating anything. The single `String` object is only actually produced once, at the end, when you call `.toString()`.

---

## Interview Gotchas

- **String methods never mutate — they always return a new object.** `name.toUpperCase();` on its own line, without capturing the result, does absolutely nothing observable.
- **`==` compares object identity, not content, and for Strings specifically, the "same content → same object" behavior is an optimization detail (the pool), not a guarantee.** Whether it happens to return `true` depends on how each String was constructed — which is often outside your control or visibility. Always use `.equals()`.
- **`new String("literal")` deliberately bypasses the pool**, creating a genuinely separate object even when an identical literal already exists in the pool.
- **`intern()` manually pulls a String into the pool** — rarely used in practice, but demonstrates real understanding of how the pool works when asked in interviews.
- **String concatenation in a loop is O(n²)-ish in the worst case** (each concatenation copies the entire accumulated string so far into a new, larger object) — use `StringBuilder` for any loop-based concatenation.

## Quick Summary

- Strings are immutable — every method that looks like it modifies a String actually returns a brand-new one; the original is never touched.
- Immutability enables the **String pool** (safe object reuse for literals), **thread safety** (no risk of concurrent mutation), and **security** (sensitive string data can't be silently altered by other code holding the same reference).
- `new String(...)` deliberately creates a separate object outside the pool, even for content that already exists in it.
- **Always use `.equals()` for String content comparison — never `==`**, since `==`'s result depends on construction details that are often invisible or out of your control.
- `intern()` manually pulls a String into the pool.
- Use `StringBuilder` for any string-building inside a loop — plain `+` concatenation creates a new object on every iteration, which is genuinely wasteful at scale.

## Code Reference

See `StringInternalsTutorial.java` (your practice file) — demonstrates immutability directly (Section 1), the pool vs `new String()` distinction (Sections 2-3), `intern()` pulling a String back into the pool (Section 4), the classic `==` unreliability trap across three variables with identical content (Section 5), and a side-by-side `+`-concatenation vs `StringBuilder` comparison producing the same output through very different internal costs (Section 6).
