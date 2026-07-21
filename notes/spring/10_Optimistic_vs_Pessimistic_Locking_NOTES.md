# Optimistic vs Pessimistic Locking — Notes

## The Problem This Solves

In any system where multiple threads/requests can read and write the same row at the same time (a wallet balance, inventory count, ticket seat), you risk a **lost update**:

1. Thread 1 reads balance = 1000
2. Thread 2 reads balance = 1000 (before Thread 1 writes back)
3. Thread 1 writes balance = 700 (deducted 300)
4. Thread 2 writes balance = 500 (deducted 500, but based on stale 1000)
5. Thread 1's update is **silently overwritten**. No error. No warning. Money just disappears from the ledger's logic.

This is the exact vulnerability an interviewer is probing when they ask "how do you prevent someone from changing a value in transit?" There are two standard solutions: **optimistic** and **pessimistic** locking.

---

## Optimistic Locking — `@Version`

**Assumption:** conflicts are rare. Don't block anyone up front — just detect conflicts when they happen, and fail loudly.

**How it works:** add a `version` column. Every UPDATE checks the version hasn't changed since you read it, and increments it.

```java
@Entity
public class Wallet extends BaseEntity {
    private BigDecimal balance;

    @Version  // JPA manages this automatically
    private Long version;
}
```

Under the hood, JPA appends the version check to the generated SQL:

```sql
UPDATE wallets SET balance = ?, version = version + 1
WHERE id = ? AND version = ?
```

If zero rows are affected (because someone else already bumped the version), JPA throws:

```
jakarta.persistence.OptimisticLockException
```

Your code catches this and typically retries the whole operation.

**Trace:**
```
Thread 1: SELECT ... → balance=1000, version=5
Thread 2: SELECT ... → balance=1000, version=5

Thread 1: UPDATE ... SET version=6 WHERE id=1 AND version=5   → matches → SUCCESS
Thread 2: UPDATE ... SET version=6 WHERE id=1 AND version=5   → version is now 6, not 5 → 0 rows affected → OptimisticLockException
```

---

## Pessimistic Locking — `SELECT ... FOR UPDATE`

**Assumption:** conflicts will happen often. Prevent them outright rather than detecting them after.

**How it works:** lock the row the moment you read it. Any other transaction trying to lock the same row for update is **blocked** — it waits, it doesn't fail.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.id = :id")
Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);
```

Generated SQL:

```sql
SELECT * FROM wallets WHERE id = ? FOR UPDATE
```

**Trace:**
```
Thread 1: SELECT ... FOR UPDATE  → lock acquired, balance=1000
Thread 2: SELECT ... FOR UPDATE  → BLOCKED, waiting...

Thread 1: UPDATE balance=700, COMMIT → lock released
Thread 2: lock acquired, reads balance=700 (correct, fresh value)
Thread 2: UPDATE balance=200, COMMIT
```

No conflict. No lost update. Thread 2 simply waited.

### LockModeType options

```java
LockModeType.PESSIMISTIC_READ   // shared lock — others can read, nobody can write
LockModeType.PESSIMISTIC_WRITE  // exclusive lock — nobody can read or write (MPPS uses this)
LockModeType.OPTIMISTIC         // uses @Version, no DB-level lock
```

---

## How MPPS Uses It

In `TransactionWorker`:

```java
// CREDIT
Wallet toWallet = walletRepository.findByIdForUpdate(event.toWalletId())
    .orElseThrow(...);
toWallet.setBalance(toWallet.getBalance().add(event.amount()));

// DEBIT
Wallet fromWallet = walletRepository.findByIdForUpdate(event.fromWalletId())
    .orElseThrow(...);
fromWallet.setBalance(fromWallet.getBalance().subtract(event.amount()));

// TRANSFER — locks BOTH wallets
Wallet fromWallet = walletRepository.findByIdForUpdate(event.fromWalletId())...
Wallet toWallet = walletRepository.findByIdForUpdate(event.toWalletId())...
```

Both wallets stay locked for the whole transaction — nobody else can touch either until it commits or rolls back.

### The deadlock risk (a real MPPS limitation)

```
Transfer A: wallet 001 → wallet 002
Transfer B: wallet 002 → wallet 001   (concurrent)

Thread 1: locks 001, waits for 002
Thread 2: locks 002, waits for 001
→ DEADLOCK, both wait forever
```

**Fix:** always lock rows in a consistent order (e.g. sort by UUID, lock the lower one first):

```java
UUID firstId  = fromWalletId.compareTo(toWalletId) < 0 ? fromWalletId : toWalletId;
UUID secondId = fromWalletId.compareTo(toWalletId) < 0 ? toWalletId : fromWalletId;

Wallet first  = walletRepository.findByIdForUpdate(firstId).orElseThrow(...);
Wallet second = walletRepository.findByIdForUpdate(secondId).orElseThrow(...);
```

Now every thread acquires locks in the same global order — no circular wait is possible.

---

## Comparison Table

| | Optimistic | Pessimistic |
|---|---|---|
| Assumption | Conflicts are rare | Conflicts are common |
| Mechanism | `@Version` column, checked on UPDATE | Row lock on read (`FOR UPDATE`) |
| On conflict | Throws `OptimisticLockException` | Other thread blocks/waits |
| Performance | Better — no blocking | Worse — threads queue |
| Risk | Lost updates if exception isn't handled | Deadlocks if lock order isn't consistent |
| Best for | Low contention (user profiles, settings) | High contention (wallet balances, inventory, seats) |

**MPPS uses pessimistic** because wallet writes are high-contention — constant retries under optimistic locking would be expensive and messy.

---

## Interview Gotchas

- **"Why not just use optimistic locking everywhere?"** → Under high contention it causes constant retries, which is worse for throughput than blocking. Optimistic is for low-conflict scenarios.
- **"Does `@Version` require you to write SQL?"** → No. JPA manages the version column and the WHERE clause automatically. You just annotate the field.
- **"What's the actual SQL for PESSIMISTIC_WRITE?"** → `SELECT ... FOR UPDATE`. Know this cold — it's the kind of detail that separates "used Spring Data JPA" from "understands what Spring Data JPA is doing."
- **"How do you prevent deadlocks with multiple locks?"** → Consistent lock ordering. This is a general database concept, not Spring-specific — applies to any system that locks multiple rows.
- **"PESSIMISTIC_READ vs PESSIMISTIC_WRITE?"** → READ is a shared lock (others can still read, nobody can write). WRITE is exclusive (nobody can read or write). MPPS wants WRITE because it doesn't want anyone reading a wallet mid-transfer either.

---

## Quick Summary

- **Lost update** = two transactions read the same value, both write, second write silently erases the first.
- **Optimistic locking** (`@Version`): assume conflicts are rare, detect them after the fact via a version check, throw `OptimisticLockException` on mismatch.
- **Pessimistic locking** (`SELECT ... FOR UPDATE` / `@Lock(PESSIMISTIC_WRITE)`): assume conflicts are common, lock the row up front, force other transactions to wait.
- MPPS uses pessimistic locking on wallets because of high write contention.
- **Deadlock** = two transactions each hold a lock the other needs. Fixed by always acquiring locks in a consistent, agreed-upon order.

## Code Reference (MPPS)

- `WalletRepository.findByIdForUpdate()` — the pessimistic lock query
- `TransactionWorker` — CREDIT / DEBIT / TRANSFER handlers that call it
- `Wallet` entity — where `@Version` would live if optimistic locking were used instead
