# Collections Internals — Full Notes

## The Problem This Solves

Almost every program needs to hold a group of things — a list of transactions, a set of unique wallet IDs, a lookup table of user ages. Java's Collections Framework gives you ready-made data structures for this, but different structures make very different tradeoffs internally. Picking the wrong one for your access pattern (e.g. constantly inserting into the middle of an `ArrayList`) can silently make your code far slower than it needs to be — the code still works, it's just quietly doing a lot more work than necessary. Understanding what's actually happening underneath each structure is what lets you pick correctly instead of guessing.

**Collection** — the umbrella term. A `List`, a `Set`, a `Map` — all are Collections. A Collection is just a container that holds multiple objects.

---

## ArrayList — An Array That Resizes Itself

Under the hood, an `ArrayList` is backed by a plain Java array. When you create one, Java allocates an array of a default size (historically 10). When you add more elements than the array can hold, Java doesn't grow it by one slot at a time — it allocates a **new**, larger array (1.5x the old size), copies every existing element into it, and discards the old array.

```java
List<String> names = new ArrayList<>();
```

| Operation | Speed | Why |
|---|---|---|
| `get(index)` | Fast | Direct memory offset calculation — jumps straight to the element |
| `add()` to the end | Fast (usually) | Just fills the next open slot — occasionally slow, when a resize is triggered |
| `add(0, x)` — insert at the front/middle | Slow | Every element after the insertion point has to be shifted one position to the right to make room |
| `remove(0)` — remove from front/middle | Slow | Every element after the removed one has to shift one position to the left to close the gap |

**Concrete trace of the slow case:**

```java
List<String> arrayList = new ArrayList<>(List.of("Alice", "Bob", "Charlie"));
arrayList.add(0, "Zara");
// Before: [Alice, Bob, Charlie]
// Step 1: shift Charlie right → [Alice, Bob, _, Charlie]
// Step 2: shift Bob right     → [Alice, _, Bob, Charlie]
// Step 3: shift Alice right   → [_, Alice, Bob, Charlie]
// Step 4: insert Zara at 0    → [Zara, Alice, Bob, Charlie]
```

Every single existing element moved — that's O(n) work for one insertion, versus O(1) for adding at the end.

**Use ArrayList when:** you need fast access by index, or you're mostly adding to the end. This is your default list choice roughly 90% of the time.

---

## LinkedList — A Chain of Nodes

Each element in a `LinkedList` is a separate node object, holding: the actual value, a pointer to the next node, and a pointer to the previous node (Java's `LinkedList` is doubly-linked).

```
[Alice] ↔ [Bob] ↔ [Charlie]
```

There is no underlying array at all — just a chain of independently-allocated node objects connected by pointers.

| Operation | Speed | Why |
|---|---|---|
| `get(index)` | Slow | There's no direct offset to jump to — Java has to start at one end and walk node-by-node, following pointers, until it counts up to the target index |
| `add(0, x)` — insert at front/middle | Fast | Only two pointers need to be rewired (the new node's `next`/`prev`, and the neighboring nodes' pointers to point to it) — nothing else moves |
| `remove()` from front/middle | Fast | Same reasoning — just rewire the surrounding pointers to skip over the removed node |

```java
LinkedList<String> linkedList = new LinkedList<>();
linkedList.add("Alice");
linkedList.add("Bob");
linkedList.add("Charlie");
linkedList.addFirst("Zara");   // fast — no shifting, just points Zara.next → Alice, and updates the head pointer
linkedList.addLast("David");   // fast — same idea, at the tail
```

**Use LinkedList when:** you frequently insert/remove from the middle of a large list. In practice, this is rare — most real-world lists are read far more often than they're restructured in the middle, which is why `ArrayList` remains the default.

---

## HashMap — An Array of Buckets

A `HashMap` stores key-value pairs. Internally, it's an array (the "bucket array"), and it uses **hashing** to decide which bucket a given key belongs in, so lookups don't require scanning every entry.

```java
Map<String, Integer> ages = new HashMap<>();
ages.put("Temi", 25);
ages.get("Temi"); // 25
```

### What actually happens on `put("Temi", 25)`

1. Java calls `"Temi".hashCode()` → produces some integer (this is deterministic — the same string always produces the same hash).
2. That integer is run through an internal formula to calculate a bucket index (essentially `hash % numberOfBuckets`, though the real implementation does some extra bit-mixing for better distribution).
3. The key-value pair is stored in that specific bucket.
4. **If that bucket already has an entry** (a "collision" — two different keys happened to map to the same bucket index), the new entry is added alongside the existing one — stored as a small linked list within that one bucket slot (in modern Java, if a single bucket gets too crowded, it upgrades internally to a balanced tree for that bucket, for better worst-case performance — but conceptually, think "linked list of collisions" first).

### What actually happens on `get("Temi")`

1. Java hashes `"Temi"` again → gets the exact same number as before (hashing is deterministic).
2. That maps to the exact same bucket index used during `put()`.
3. **If there are multiple entries in that bucket** (from a collision), Java walks through them and uses `.equals()` on each one to find the entry whose key actually matches `"Temi"` — the hash alone only narrows it down to "which bucket," `.equals()` is what confirms "which exact key."
4. Returns the matching value.

**Real collision example:** in Java, the strings `"Aa"` and `"BB"` happen to produce the exact same `hashCode()`. They'll land in the same bucket. Retrieval still works correctly, because after landing in the shared bucket, `.equals()` is used to distinguish between them:

```java
Map<String, String> collisionMap = new HashMap<>();
collisionMap.put("Aa", "value1");
collisionMap.put("BB", "value2");  // same hashCode as "Aa" — lands in the same bucket
System.out.println(collisionMap.get("Aa")); // "value1" — found via equals(), despite the shared bucket
System.out.println(collisionMap.get("BB")); // "value2" — same story
```

### Key facts

- **No ordering** — elements are stored according to hash-derived bucket positions, not insertion order. Never rely on iteration order from a plain `HashMap`.
- **One null key allowed.**
- **Not thread-safe** — concurrent modification from multiple threads can corrupt internal structure. Use `ConcurrentHashMap` for multithreaded scenarios.
- **Default capacity: 16 buckets**, growing (roughly doubling) as more entries are added, to keep the average bucket short.

### Useful methods, in practice

```java
ages.put("Temi", 28);                        // overwrites the existing value for an existing key — no error, just replaced
int age = ages.getOrDefault("Unknown", 0);   // returns 0 instead of null if "Unknown" isn't a key — avoids a manual null check
ages.containsKey("Ada");                     // true/false, without needing to call get() and check for null

for (Map.Entry<String, Integer> entry : ages.entrySet()) {
    System.out.println(entry.getKey() + " is " + entry.getValue());
}
```

### Interview Gotcha — the hashCode + equals contract for HashMap keys

A `HashMap` uses `hashCode()` to pick the bucket, then `equals()` to find the exact key within that bucket. If you use a custom object as a key **without overriding both** methods:

- Two logically-identical objects can produce **different default hashCodes** (the default `hashCode()` is based on memory address, not content).
- They land in **different buckets entirely**.
- Calling `map.get(logicallyEqualButDifferentObjectInstance)` will silently return `null` — even though, conceptually, "that key" is already in the map. No exception, no warning — just a wrong answer, because the lookup never even looked in the right bucket.

This is exactly why `equals()` and `hashCode()` must always be overridden together — covered in full detail in the equals/hashCode notes.

---

## HashSet — A HashMap in Disguise

A `HashSet` is, quite literally, a `HashMap` under the hood where every value is a shared dummy placeholder object. The actual "set" behavior comes entirely from `HashMap`'s key-uniqueness guarantee.

```java
Set<String> names = new HashSet<>();
names.add("Temi");      // internally: map.put("Temi", DUMMY)
names.add("Temi");      // internally: map.put("Temi", DUMMY) again — same key, silently overwrites, size doesn't grow
System.out.println(names.size()); // 1
```

- **No duplicates** — adding the same element twice does nothing (the second `put` just overwrites the same key with the same dummy value).
- **`contains()` is instant** — it's really just `map.containsKey()` under the hood, so it uses hashing rather than scanning every element one by one.
- **No ordering**, for the same reason `HashMap` has none.

**Concrete demonstration of the speed difference vs a List:**

```java
List<String> listIds = new ArrayList<>(List.of("id1", "id2", "id3"));
Set<String> setIds = new HashSet<>(List.of("id1", "id2", "id3"));

listIds.contains("id3");  // scans element by element until found — gets slower as the list grows
setIds.contains("id3");   // hashes "id3", jumps straight to its bucket — stays fast regardless of size
```

**Use HashSet when:** you need to guarantee unique elements, or you need to repeatedly check "have I seen this before?" quickly.

---

## When to Use What — Decision Table

| | ArrayList | LinkedList | HashMap | HashSet |
|---|---|---|---|---|
| Access by index | ✅ Fast | ❌ Slow | — | — |
| Add/remove in the middle | ❌ Slow | ✅ Fast | — | — |
| Key-value lookup | — | — | ✅ Fast | — |
| Guarantee unique elements | ❌ | ❌ | — | ✅ |
| Default choice | ✅ Yes | ❌ Rarely | ✅ Yes | ✅ Yes |

---

## Interview Gotchas — Summary

- **ArrayList vs LinkedList is about where you insert/remove, not just "which is generally faster."** ArrayList wins on indexed reads and end-inserts; LinkedList wins only on frequent middle-insertion, which is rare in practice.
- **HashMap collisions don't break correctness** — two keys landing in the same bucket still resolve correctly via `equals()`. Collisions only cost you *speed* (a bucket with many collisions degrades toward linear-scan performance for that bucket), not correctness.
- **Using a custom object as a HashMap/HashSet key without overriding both `equals()` and `hashCode()` is a silent bug** — lookups will return null/false for logically-identical objects, with no exception raised anywhere.
- **HashSet has no ordering guarantee**, same as HashMap — never write code that depends on iteration order from either.

## Quick Summary

- **ArrayList** = resizable array. Fast indexed reads, slow middle inserts/removes (has to shift elements). Default list choice.
- **LinkedList** = chain of nodes with next/prev pointers. Fast middle inserts/removes (just rewires pointers), slow indexed reads (has to walk node by node). Rarely the right choice in practice.
- **HashMap** = an array of buckets. Uses `hashCode()` to pick the bucket, `equals()` to find the exact key inside that bucket if there's a collision.
- **HashSet** = a HashMap wearing a trench coat — every "value" is a shared dummy object; uniqueness and fast `contains()` come directly from HashMap's key mechanics.

## Code Reference

See `CollectionsTutorial.java` (your practice file) — Section 5 demonstrates the ArrayList-vs-HashSet `contains()` speed difference directly, and Section 6 demonstrates a real hash collision (`"Aa"` and `"BB"`) resolving correctly via `equals()`.
