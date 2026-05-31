# 05 — equals() and hashCode()

## The problem
Every class inherits default equals() from Object.
Default equals() behaves exactly like == — compares memory address, not content.

```java
BankAccount a1 = new BankAccount("001", "Temi");
BankAccount a2 = new BankAccount("001", "Temi");
a1.equals(a2); // false — different objects in memory, even though content is same
```

## == vs equals()
- `==` — are these the exact same object in memory?
- `equals()` — are these logically the same thing?

## Fixing it — override equals()
```java
@Override
public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    BankAccount other = (BankAccount) obj;
    return this.accountNumber.equals(other.accountNumber);
}
```

## Why you MUST also override hashCode()
HashMap uses hashCode to find the bucket, then equals to find the key inside.

If you override equals but NOT hashCode:
- Two equal objects get different hashCodes by default
- They land in different buckets
- map.get(a2) returns null even though a2.equals(a1) is true
- Silent bug — no error, just wrong behaviour

## The contract
**If two objects are equal according to equals(), they must have the same hashCode().**

## Fixing it — override hashCode()
```java
@Override
public int hashCode() {
    return Objects.hash(accountNumber); // same field used in equals
}
```

## Which field to use?
Always use the field that **uniquely identifies** the object.
- BankAccount → accountNumber
- User → userId or email
- Transaction → transactionId

Never use amount, description, or other non-unique fields.

## Interview gotchas
- Overriding equals without hashCode = silent HashMap bug
- Default equals() is the same as == — compares memory address
- The contract is one-way: equal objects must have same hashCode, but same hashCode doesn't mean equal (that's just a collision)
- IntelliJ generates both — but you must understand why

## Golden rule
**Always override both together. Never one without the other.**

## Quick summary
- Default equals() compares memory address — usually wrong for your own classes
- Override equals() to compare by meaningful fields
- Override hashCode() with the same fields — or HashMap/HashSet breaks silently
- Use the uniquely identifying field in both

## Code practice
See: `java-core/EqualsHashCodeTutorial.java`
