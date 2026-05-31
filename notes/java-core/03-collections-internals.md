# 03 — Collections Internals

## Collection
 A Collection is just a container that holds multiple objects. A List, a Set, a Map — all Collections.

## ArrayList
An array that resizes itself automatically. Default size 10, grows 1.5x when full.

```java
List<String> names = new ArrayList<>();
```

| Operation | Speed | Why |
|---|---|---|
| `get(index)` | Fast | Direct access by index |
| `add()` to end | Fast | Usually just fills next slot |
| `add(0, x)` middle | Slow | Shifts every element right |
| `remove(0)` middle | Slow | Shifts every element left |

**Use when:** You need to access elements by index, or mostly add to the end. Default choice 90% of the time.

---

## LinkedList
Each element is a node holding a value + pointer to next + pointer to previous.
`[Alice] ↔ [Bob] ↔ [Charlie]`

| Operation | Speed | Why |
|---|---|---|
| `get(index)` | Slow | Walks from node 1 counting to index |
| `add(0, x)` middle | Fast | Just rewires two pointers |
| `remove()` middle | Fast | Just rewires two pointers |

**Use when:** You frequently add/remove from the middle. Rarely needed in practice.

---

## HashMap
An array of buckets internally. Uses hashing to store and retrieve key-value pairs fast.

```java
Map<String, Integer> ages = new HashMap<>();
ages.put("Temi", 25);
ages.get("Temi"); // 25
```

**What happens on `put("Temi", 25)`:**
1. Java calls `"Temi".hashCode()` → gets a number
2. Number is used to calculate bucket index
3. Key-value pair stored in that bucket
4. If bucket already has an entry → collision → stored as linked list in that bucket

**What happens on `get("Temi")`:**
1. Hash "Temi" → same number → same bucket
2. If multiple entries in bucket, use `.equals()` to find exact key
3. Return value

**Key facts:**
- No ordering — elements stored in unpredictable order
- One null key allowed
- Not thread safe — use `ConcurrentHashMap` for multithreaded code
- Default 16 buckets

## Interview gotcha — hashCode + equals contract
HashMap uses `hashCode` to find the bucket and `equals` to find the key inside.
If you use an object as a key without overriding both:
- Different hashCodes → lands in wrong bucket → `get()` never finds it → silent bug
- Always override both together

---

## HashSet
Literally just a HashMap under the hood where every value is a dummy placeholder.

```java
Set<String> names = new HashSet<>();
names.add("Temi");      // internally: map.put("Temi", DUMMY)
names.add("Temi");      // ignored — key already exists
System.out.println(names.size()); // 1
```

- No duplicates — adding same element twice does nothing
- `contains()` is instant — uses hashing, not a linear search
- No ordering

**Use when:** You need unique elements OR need to check if something exists quickly.

---

## When to use what

| | ArrayList | LinkedList | HashMap | HashSet |
|---|---|---|---|---|
| Access by index | ✅ Fast | ❌ Slow | — | — |
| Add/remove middle | ❌ Slow | ✅ Fast | — | — |
| Key-value lookup | — | — | ✅ Fast | — |
| Unique elements | ❌ | ❌ | — | ✅ |
| Default choice | ✅ Yes | ❌ Rarely | ✅ Yes | ✅ Yes |

## Quick summary
- ArrayList = resizable array. Fast reads, slow middle inserts. Default list choice.
- LinkedList = chain of nodes. Fast middle inserts, slow reads. Rarely used.
- HashMap = array of buckets. Uses hashCode to find bucket, equals to find key.
- HashSet = HashMap with dummy values. Fast uniqueness checks.

## Code practice
See: `java-core/CollectionsTutorial.java`
