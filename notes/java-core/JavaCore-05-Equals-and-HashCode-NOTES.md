# equals() and hashCode() — Full Notes

## The Problem This Solves

Every class in Java, whether you wrote it or not, silently inherits a default `equals()` method from `Object` — because every class ultimately extends `Object`, whether you write `extends Object` or not. That default implementation does something you almost never actually want: it behaves exactly like `==`, comparing whether two references point to the **exact same object in memory**, not whether they represent the same real-world thing.

```java
BankAccount a1 = new BankAccount("001", "Temi");
BankAccount a2 = new BankAccount("001", "Temi");

a1.equals(a2); // false — even though every field is identical, these are two SEPARATE objects in memory
```

This is almost never the behavior you actually want for your own domain classes. If two `BankAccount` objects both represent account number "001", you want them to be treated as "the same account" — logically equal — regardless of whether they happen to be two separately-constructed Java objects in memory.

---

## `==` vs `equals()` — the distinction

- **`==`** asks: "are these the exact same object in memory?" (reference identity)
- **`equals()`** asks: "are these logically/conceptually the same thing?" (whatever *you* define that to mean)

By default, before you override anything, both questions have the same answer, because the default `equals()` is literally implemented as `==` under the hood. Overriding `equals()` is how you teach Java what "the same" actually means for your specific class.

---

## Fixing It — Overriding `equals()`

```java
@Override
public boolean equals(Object obj) {
    if (this == obj) return true;              // fast path: literally the same object → definitely equal
    if (obj == null) return false;               // nothing is equal to null
    if (getClass() != obj.getClass()) return false;  // must be the exact same class, not just "compatible"
    BankAccount other = (BankAccount) obj;       // safe to cast now — we've confirmed the class matches
    return this.accountNumber.equals(other.accountNumber);  // compare using the field that DEFINES identity
}
```

Walking through each line and why it exists:
- **`this == obj`** — a cheap shortcut. If it's literally the same reference, there's no point doing further comparison work; they're trivially equal.
- **`obj == null`** — required, because calling `.getClass()` on `null` would throw an NPE, and by contract, nothing should ever be considered equal to null.
- **`getClass() != obj.getClass()`** — ensures you're not comparing a `BankAccount` to some unrelated type (or even a subclass) that happens to have a similar shape. This guards against subtle bugs where a subclass might otherwise be considered "equal" to its parent despite having different meaningful state.
- **The final comparison** — this is the actual business decision: *what field(s) make two accounts "the same account"?* Here, it's `accountNumber`.

---

## Why You MUST Also Override `hashCode()`

This is the part that causes silent, hard-to-diagnose bugs if skipped. Recall from the Collections notes: a `HashMap` uses `hashCode()` to pick which bucket a key belongs in, and only then uses `equals()` to find the exact match within that bucket.

**If you override `equals()` but leave the default `hashCode()` in place:**

```java
class HalfFixedAccount {
    private String accountNumber;
    private String owner;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        HalfFixedAccount other = (HalfFixedAccount) obj;
        return this.accountNumber.equals(other.accountNumber);
    }
    // hashCode NOT overridden — still using the default, memory-address-based one
}
```

```java
HalfFixedAccount h1 = new HalfFixedAccount("001", "Temi");
HalfFixedAccount h2 = new HalfFixedAccount("001", "Temi");

h1.equals(h2);  // true — equals() says these are the same account

Map<HalfFixedAccount, Double> halfMap = new HashMap<>();
halfMap.put(h1, 5000.0);
halfMap.get(h2);  // null ← SILENT BUG
```

**Why does this happen, mechanically?** `h1` and `h2` are two separate objects in memory, so the *default* `hashCode()` (which is derived from memory address, not content) produces **different hash values** for them, even though `equals()` considers them the same account. Because the hash values differ, `h1` and `h2` get placed in **different buckets** in the `HashMap`. When you call `halfMap.get(h2)`, Java hashes `h2`, jumps straight to *h2's* bucket — and never even looks in the bucket where `h1` actually lives. It's not that `equals()` failed; it's that the lookup never got the chance to call `equals()` at all, because it was looking in the wrong bucket entirely.

**There is no exception, no warning, nothing in the console.** The code runs, returns `null`, and looks completely normal unless you specifically know to expect `5000.0`. This is exactly the kind of bug that passes code review and shows up as "weird" production behavior weeks later.

---

## The Contract

**If two objects are equal according to `equals()`, they must have the same `hashCode()`.**

This is a one-directional rule, and the direction matters:
- Equal objects → **must** have the same hashCode (mandatory).
- Same hashCode → does **not** mean the objects are equal (this is just a hash collision — two logically different objects can share a hashCode by coincidence, and that's fine and expected, as covered in the Collections notes with `"Aa"` and `"BB"`).

---

## Fixing It — Overriding `hashCode()`

```java
@Override
public int hashCode() {
    return Objects.hash(accountNumber); // MUST use the same field(s) used in equals()
}
```

`Objects.hash(...)` is a JDK utility that combines one or more values into a single well-distributed hash code — it's the standard, idiomatic way to implement `hashCode()` in modern Java, rather than writing the hashing math by hand.

### With both overridden correctly

```java
class BankAccount {
    private String accountNumber;
    private String owner;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        BankAccount other = (BankAccount) obj;
        return this.accountNumber.equals(other.accountNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountNumber);  // same field as equals()
    }
}
```

```java
BankAccount a1 = new BankAccount("001", "Temi");
BankAccount a2 = new BankAccount("001", "Temi");

a1.equals(a2);                       // true
a1.hashCode() == a2.hashCode();      // true — same bucket, guaranteed by the contract

Map<BankAccount, Double> balances = new HashMap<>();
balances.put(a1, 5000.0);
balances.get(a2);                    // 5000.0 — correctly found, because a1 and a2 land in the SAME bucket
```

And correctly deduplicated in a `HashSet` (which, as covered in the Collections notes, is really just a `HashMap` under the hood):

```java
Set<BankAccount> accounts = new HashSet<>();
accounts.add(a1);
accounts.add(a2);  // logically the same account — correctly rejected as a duplicate
accounts.size();   // 1
```

---

## Which Field Should You Actually Use?

Always use the field (or fields) that **uniquely identifies** the object in the real-world domain it represents — not just any field that happens to differ between instances.

- `BankAccount` → `accountNumber` (the thing that actually distinguishes one account from another)
- `User` → `userId` or `email` (not, say, `firstName`, which many users could share)
- `Transaction` → `transactionId`

**Never** use fields like `amount`, `description`, or other non-unique attributes — two genuinely different transactions might coincidentally have the same amount, but that doesn't make them the same transaction.

---

## A Related Gotcha — Strings, `==`, and `equals()`

This connects directly to the String Internals topic:

```java
String s1 = new String("Temi");
String s2 new String("Temi");

s1 == s2;        // false — two separate objects in memory (new String() bypasses the string pool)
s1.equals(s2);   // true — same content, and String has ALREADY correctly overridden equals() for you
```

`String` is a good example of a class that gets `equals()`/`hashCode()` right out of the box (comparing content, not memory address) — which is exactly the behavior you want to replicate for your own domain classes like `BankAccount`.

---

## Interview Gotchas

- **Overriding `equals()` without `hashCode()` causes a silent `HashMap`/`HashSet` bug** — lookups for logically-equal objects can return `null`/`false`, with zero exceptions or warnings anywhere.
- **The default `equals()` is identical to `==`** — pure memory-address comparison. This is almost never the right behavior for a domain class you've written yourself.
- **The contract is one-directional**: equal objects must share a hashCode, but sharing a hashCode doesn't imply equality (that's just a collision, and it's expected/handled correctly via `equals()`).
- **IntelliJ (and most IDEs) can auto-generate both methods for you** — but you're still expected to understand *why* they're implemented the way they are, and to correctly pick the identifying field(s) yourself; the IDE doesn't know your domain's business rules.

## Golden Rule

**Always override both together. Never override one without the other.**

## Quick Summary

- Default `equals()` behaves exactly like `==` — comparing memory addresses, not content — which is almost always wrong for domain classes.
- Override `equals()` to define what "logically the same" means for your class, using its uniquely-identifying field(s).
- **You must also override `hashCode()` using the exact same field(s)**, or `HashMap`/`HashSet` will silently fail to find logically-equal objects, because they'll land in different buckets.
- Contract: equal objects **must** share a hashCode. Sharing a hashCode does **not** imply equality — that's just a collision, resolved correctly by `equals()`.
- Always base both methods on the field(s) that genuinely, uniquely identify the object in your domain — never on incidental or non-unique fields.

## Code Reference

See `EqualsHashCodeTutorial.java` (your practice file) — walks through all three states side by side: `BrokenAccount` (neither overridden — pure `==` behavior), `HalfFixedAccount` (only `equals()` overridden — reproduces the exact silent `HashMap` bug described above, live), and `BankAccount` (both correctly overridden — working `HashMap`/`HashSet` behavior).
