# 06 — String Internals

## Immutability
Strings in Java can never be changed after creation.
Every String method returns a NEW String — the original is untouched.

```java
String name = "Temi";
name.toUpperCase();           // creates new String "TEMI", doesn't change name
System.out.println(name);    // still "Temi"

String upper = name.toUpperCase(); // capture the new String
System.out.println(upper);   // "TEMI"
```

## Why immutability matters (interview answer)
- **Security** — account numbers, passwords can't be modified after creation. Prevents tampering mid-transaction.
- **Thread safety** — multiple threads can share the same String safely, nobody can change it
- **Enables the String pool** — only safe to reuse objects that can't be changed

## The String Pool
Java keeps a pool of String objects in memory.
When you create a String literal, Java checks the pool first.
If that value already exists — it reuses the same object instead of creating a new one.

```java
String a = "Temi";
String b = "Temi";
a == b; // true — same object in pool
```

## new String() — bypasses the pool
Forces Java to create a brand new object in regular memory, ignoring the pool.

```java
String a = "Temi";              // pool
String b = new String("Temi");  // new object, ignores pool

a == b;        // false — different objects in memory
a.equals(b);   // true — same content
```

## Why == is unreliable for Strings
Depending on how a String was created, == may return true or false for the same content.
Always use .equals() to compare String content. Never ==.

## intern() 
Pulls a String into the pool manually.
```java
String a = "Temi";
String b = new String("Temi").intern(); // now in pool
a == b; // true
```
Rarely used in practice but interviewers ask about it.

## Performance trap — concatenation in loops
```java
// BAD — creates a new String object every iteration
String result = "";
for (Transaction t : transactions) {
    result = result + t.getId(); // 10,000 new String objects
}

// GOOD — StringBuilder modifies same object in place
StringBuilder sb = new StringBuilder();
for (Transaction t : transactions) {
    sb.append(t.getId());
}
String result = sb.toString(); // one String at the end
```

## Quick summary
- Strings are immutable — methods return new Strings, original never changes
- String pool reuses objects for literals — saves memory
- new String() bypasses the pool — creates separate object
- Always use .equals() for String comparison, never ==
- Use StringBuilder for concatenation in loops

## Code practice
See: `java-core/StringInternalsTutorial.java`
