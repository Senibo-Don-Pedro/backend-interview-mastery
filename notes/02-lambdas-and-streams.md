# 02 — Lambdas and Streams

## Lambdas
A short way to write an instruction inline without defining a separate method.

```java
// Pattern: (input) -> what to do with it
name -> name.toUpperCase()        // given name, return uppercase
(a, b) -> a + b                   // given a and b, return sum
() -> System.out.println("hello") // given nothing, print hello

```

## What is a Stream?

A pipeline for processing a list. It does not modify the original list.

`List → .stream() → filter → map → collect → Result`

### Two types of operations:

* **Intermediate** — lazy, don't execute until terminal fires. Examples: `filter`, `map`, `sorted`, `distinct`, `limit`.
* **Terminal** — triggers execution, produces result. Examples: `collect`, `toList`, `forEach`, `reduce`, `count`.

## Key operations

```java
// filter — keeps elements matching condition. Type stays the same.
// Example: Keep only names that start with "A"
.filter(name -> name.startsWith("A"))

// map — transforms each element. Type can change.
// Example: Transform a List of Strings into a List of Integers representing their lengths
.map(name -> name.length())   

// reduce — folds everything into one value
// Example: Sum up a stream of numbers, starting at 0
.reduce(0, (a, b) -> a + b)   

// forEach — runs action on each, returns nothing (terminal)
// Example: Print out every element to the console
.forEach(name -> System.out.println(name))

// collect / toList — gathers results into a list (terminal)
// Example: Gather the processed stream back into a List
.collect(Collectors.toList())
.toList()   // Java 16+ shorthand

```

## Real example

```java
List<String> result = users.stream()
    .filter(u -> u.isActive())
    .map(u -> u.getName().toUpperCase())
    .toList();

```

## Interview gotchas

1. **Always filter before map**
Filter reduces the number of elements first — map then transforms only what's left.
Flipping it means transforming everything before filtering. Wasteful.
2. **Lazy evaluation — most important concept**
Intermediate operations do nothing on their own. Nothing runs until a terminal operation is called.
Remove `.toList()` from a stream — nothing executes at all.
3. **A stream can only be used once**
Calling a terminal operation consumes the stream. Using it again throws `IllegalStateException`.
Always create a fresh stream from the source.

## Quick summary

* **Lambda** = short inline instruction. Left of `->` is input, right is what to do.
* **Streams** process lists without modifying the original.
* **Filter** keeps, **map** transforms, **reduce** combines, **collect** gathers.
* Nothing runs until a **terminal operation** triggers the pipeline.

