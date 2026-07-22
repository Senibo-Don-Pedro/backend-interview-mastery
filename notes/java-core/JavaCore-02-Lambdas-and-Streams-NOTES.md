# Lambdas and Streams — Full Notes

## The Problem This Solves

Before lambdas (pre-Java 8), passing "a piece of behavior" as an argument required a full anonymous class:

```java
// The old way — implementing a whole Comparator just to say "compare by length"
List<String> names = new ArrayList<>(List.of("Charlie", "Ada", "Bo"));
Collections.sort(names, new Comparator<String>() {
    @Override
    public int compare(String a, String b) {
        return a.length() - b.length();
    }
});
```

All of that ceremony — the anonymous class, the `@Override`, the method signature — exists purely to smuggle one line of actual logic (`a.length() - b.length()`) into a method that needs behavior as a parameter. A **lambda** is Java's way of writing just that one line, inline, without the surrounding boilerplate:

```java
Collections.sort(names, (a, b) -> a.length() - b.length());
```

And separately: before Java 8, processing a list (filter it, transform it, then collect results) meant writing manual loops with intermediate lists at every step. **Streams** exist to describe that whole "filter, transform, collect" pipeline declaratively — you describe *what* you want, not the loop mechanics of *how* to get there.

---

## Part 1 — Lambdas

A lambda is a short way to write a piece of logic inline, wherever Java expects "some code you'll run later."

```java
// Pattern: (input) -> what to do with it
name -> name.toUpperCase()          // given a name, return its uppercase version
(a, b) -> a + b                     // given a and b, return their sum
() -> System.out.println("hello")   // given nothing, print "hello"
```

The left side of `->` is the input (parameters), the right side is what to do with it (the body). If there's exactly one statement, you don't need `{}` or `return` — it's implied.

```java
// with braces + return, for multi-line logic
(a, b) -> {
    int sum = a + b;
    return sum * 2;
};
```

### Why this works — functional interfaces

Lambdas aren't magic — they're shorthand for implementing an interface that has exactly **one abstract method**. Java calls these "functional interfaces." `Runnable`, `Comparator`, `Function<T,R>`, `Predicate<T>`, `Consumer<T>` are all functional interfaces built into the JDK. A lambda's shape (its parameters and return type) has to match that one abstract method's signature — that's how Java knows what `(a, b) -> a + b` is even supposed to mean; it's inferred from context.

---

## Part 2 — What a Stream Actually Is

A **stream** is a pipeline for processing a sequence of elements. Critically: **it does not modify the original collection.** It reads from a source, applies a series of transformations, and produces a result — the original `List` you called `.stream()` on is completely untouched afterward.

```
List → .stream() → filter → map → collect → Result
```

### Two categories of stream operations

**Intermediate operations** — lazy. They describe a step in the pipeline but don't actually execute anything by themselves.
```java
.filter(...)   // keep matching elements
.map(...)      // transform each element
.sorted()      // sort elements
.distinct()    // remove duplicates
.limit(n)      // cap the number of elements
```

**Terminal operations** — these are what actually trigger the whole pipeline to run and produce a real result.
```java
.collect(...)  // gather results into a collection
.toList()      // shorthand for collecting into a List (Java 16+)
.forEach(...)  // run an action on each element, produces nothing
.reduce(...)   // fold everything into a single value
.count()       // count elements
```

---

## Part 3 — Lazy Evaluation, In Detail (the Most Important Concept Here)

This is the concept that trips people up the most, so let's really sit with it. Intermediate operations don't run when you call them — they just get *registered* as steps in the pipeline. **Nothing actually executes until a terminal operation is called.**

```java
names.stream()
    .filter(name -> {
        System.out.println("filtering: " + name);  // this print statement...
        return name.length() > 3;
    })
    .map(name -> name.toUpperCase());
    // ...NEVER RUNS. No terminal operation was called.
    // This entire block executes silently doing nothing.
```

Add a terminal operation, and suddenly everything fires:

```java
List<String> result = names.stream()
    .filter(name -> {
        System.out.println("filtering: " + name);  // NOW this prints, for every element
        return name.length() > 3;
    })
    .map(name -> name.toUpperCase())
    .toList();  // ← the terminal operation. This is what actually kicks off execution.
```

**Why does this matter practically?** If you build a stream pipeline and forget the terminal operation (a genuinely easy mistake — you write `.filter().map()` and get distracted before adding `.toList()`), your code compiles fine and runs without any error, but silently does nothing at all. This is one of the sneakier bugs in Java, precisely because there's no exception, no warning — just a pipeline sitting there, never triggered.

---

## Part 4 — Key Operations, With Real Examples

```java
// filter — keeps elements matching a condition. TYPE STAYS THE SAME.
List<String> namesWithA = names.stream()
    .filter(name -> name.startsWith("A"))
    .toList();
// input: List<String> → output: List<String> (same type, fewer elements)

// map — transforms each element. TYPE CAN CHANGE.
List<Integer> lengths = names.stream()
    .map(name -> name.length())
    .toList();
// input: List<String> → output: List<Integer> (different type entirely)

// reduce — folds everything down into ONE value
Integer totalLength = names.stream()
    .map(name -> name.length())
    .reduce(0, (a, b) -> a + b);
    // 0 is the starting value (the "identity")
    // (a, b) -> a + b combines the running total (a) with each new element (b)

// forEach — runs an action per element, returns nothing. TERMINAL.
names.forEach(name -> System.out.println(name));

// collect / toList — gathers the stream's results back into a real List. TERMINAL.
List<String> result = names.stream()
    .filter(n -> n.length() > 3)
    .collect(Collectors.toList());   // the classic way
List<String> result2 = names.stream()
    .filter(n -> n.length() > 3)
    .toList();                       // Java 16+ shorthand — prefer this in new code
```

### A full real-world pattern

```java
List<String> result = users.stream()
    .filter(u -> u.isActive())              // keep only active users
    .map(u -> u.getName().toUpperCase())    // transform to uppercase name
    .toList();                              // trigger execution, gather into a List
```

---

## Interview Gotchas

### 1. Always filter before you map

```java
// GOOD — filters down to fewer elements FIRST, only transforms what survives
users.stream()
    .filter(u -> u.isActive())
    .map(u -> expensiveTransform(u))
    .toList();

// WASTEFUL — transforms EVERYONE first, including users about to be discarded
users.stream()
    .map(u -> expensiveTransform(u))
    .filter(u -> u.isActive())
    .toList();
```

If `expensiveTransform` is costly (a DB call, a heavy computation), doing it before filtering means paying that cost for every element, including ones you're about to throw away. Filtering first reduces the working set before you spend effort transforming it.

### 2. Lazy evaluation — covered in depth above, but as a one-line reminder for interviews

"Intermediate operations (`filter`, `map`, etc.) are lazy — they build a pipeline description but execute nothing. Only a terminal operation (`toList()`, `forEach()`, `collect()`, etc.) actually triggers execution."

### 3. A stream can only be consumed once

```java
Stream<String> stream = names.stream()
    .filter(n -> n.length() > 3);

stream.forEach(System.out::println);   // consumes the stream — runs fine
stream.forEach(System.out::println);   // 💥 IllegalStateException: stream has already been operated upon or closed
```

Once a terminal operation runs, the stream is "spent" — you cannot reuse the same stream object again, even for a different terminal operation. If you need to process the same source data twice, call `.stream()` again on the original collection to get a fresh stream.

---

## Quick Summary

- **Lambda** = a short, inline way to write "a piece of behavior" without the boilerplate of an anonymous class — works because Java infers the shape from a matching functional interface (single abstract method).
- **Streams** describe a data-processing pipeline over a collection without modifying the original.
- **Intermediate operations** (`filter`, `map`, `sorted`, `distinct`, `limit`) are lazy — they don't run until a terminal operation fires.
- **Terminal operations** (`collect`, `toList`, `forEach`, `reduce`, `count`) trigger the whole pipeline to actually execute.
- **Filter before you map** to avoid transforming elements you're about to discard.
- **A stream is single-use** — calling a terminal operation twice on the same stream object throws `IllegalStateException`. Get a fresh stream from the source if you need to process it again.

## Code Reference

See `ListAndStreamsTutorial.java` (your practice file) — Section 6 is the deliberate demonstration of lazy evaluation: the filter/map chain there has no terminal operation and genuinely prints nothing when run, which is the exact gotcha described above made concrete.
