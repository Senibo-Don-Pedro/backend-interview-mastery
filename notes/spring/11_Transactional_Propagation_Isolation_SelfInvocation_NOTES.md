# @Transactional — Propagation, Isolation, and Self-Invocation

## The Problem This Solves

Imagine MPPS processing a transfer with two separate database writes:

```java
public void transfer(UUID fromId, UUID toId, BigDecimal amount) {
    Wallet from = walletRepository.findByIdForUpdate(fromId).orElseThrow();
    from.setBalance(from.getBalance().subtract(amount));
    walletRepository.save(from);

    // 💥 crash or exception here

    Wallet to = walletRepository.findByIdForUpdate(toId).orElseThrow();
    to.setBalance(to.getBalance().add(amount));
    walletRepository.save(to);
}
```

If something fails between the debit and the credit, money has left one wallet and never arrived at the other. You need a guarantee: **either both writes succeed, or neither does.** That guarantee is a database transaction. `@Transactional` is Spring's way of wrapping a method in one without you writing manual commit/rollback/connection-management code.

---

## How @Transactional Actually Works — Proxies

Spring doesn't rewrite your class when it sees `@Transactional`. It creates a **proxy** — a wrapper object that sits in front of your real bean.

```
Caller → Proxy.method() → [BEGIN TRANSACTION] → RealObject.method() → [COMMIT or ROLLBACK]
```

- If the method returns normally → the proxy commits.
- If the method throws an exception → the proxy may roll back (see the gotcha below — "may" is doing a lot of work there).

This is why `@Transactional` only works on **public methods called from outside the class**, and only on **Spring-managed beans** — private methods and self-invocation bypass the proxy entirely (more on this below).

```java
@Transactional
public void transfer(UUID fromId, UUID toId, BigDecimal amount) {
    Wallet from = walletRepository.findByIdForUpdate(fromId).orElseThrow();
    from.setBalance(from.getBalance().subtract(amount));

    Wallet to = walletRepository.findByIdForUpdate(toId).orElseThrow();
    to.setBalance(to.getBalance().add(amount));
    // Spring commits automatically if this returns normally
    // Spring rolls back automatically on an unchecked exception
}
```

---

## Gotcha #1: The Checked-Exception Rollback Trap

**By default, Spring only rolls back on unchecked exceptions** (`RuntimeException` and its subclasses, plus `Error`). Checked exceptions (anything extending `Exception` but not `RuntimeException`) are treated as expected business outcomes — Spring assumes you're deliberately handling them, so it **commits the transaction anyway**, even though something went wrong.

```java
// Checked exception — Spring does NOT know to roll back on this
public class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

@Transactional
public void transfer(UUID fromId, UUID toId, BigDecimal amount) throws InsufficientFundsException {
    Wallet from = walletRepository.findByIdForUpdate(fromId).orElseThrow();
    from.setBalance(from.getBalance().subtract(amount));
    walletRepository.save(from);   // ← gets COMMITTED

    if (from.getBalance().compareTo(BigDecimal.ZERO) < 0) {
        throw new InsufficientFundsException("Balance went negative");
        // Spring sees a checked exception → does NOT roll back
        // The debit above is already committed. Money is gone. Credit never happened.
    }

    Wallet to = walletRepository.findByIdForUpdate(toId).orElseThrow();
    to.setBalance(to.getBalance().add(amount));
    walletRepository.save(to);
}
```

**Fix 1 — explicitly declare which exceptions should roll back:**

```java
@Transactional(rollbackFor = InsufficientFundsException.class)
public void transfer(UUID fromId, UUID toId, BigDecimal amount) throws InsufficientFundsException {
    // now Spring rolls back the entire transaction on this exception,
    // even though it's checked
}
```

**Fix 2 — the simpler, more common approach: make your exceptions unchecked:**

```java
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
// @Transactional now rolls back automatically, no rollbackFor needed
```

---

## Propagation — How Nested @Transactional Calls Behave

Propagation controls what happens when a `@Transactional` method calls another `@Transactional` method — does the inner call join the existing transaction, or start its own?

```java
@Transactional(propagation = Propagation.REQUIRED)   // DEFAULT
// Joins the existing transaction if one is active.
// Starts a new one if none exists.

@Transactional(propagation = Propagation.REQUIRES_NEW)
// Always starts a brand-new transaction, suspending any existing one.
// The outer transaction pauses until this one commits or rolls back.

@Transactional(propagation = Propagation.NESTED)
// Creates a savepoint inside the existing transaction.
// If this method fails, only rolls back to that savepoint — not the whole transaction.
```

**MPPS example** — you want an audit log entry to survive even if the transfer itself later fails:

```java
@Transactional
public void transfer(UUID fromId, UUID toId, BigDecimal amount) {
    Wallet from = walletRepository.findByIdForUpdate(fromId).orElseThrow();
    from.setBalance(from.getBalance().subtract(amount));

    auditService.logAttempt(fromId, toId, amount);  // must survive even if transfer fails

    if (from.getBalance().compareTo(BigDecimal.ZERO) < 0) {
        throw new InsufficientFundsException("Balance went negative");
    }
    // ... credit logic
}

@Service
public class AuditService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAttempt(UUID fromId, UUID toId, BigDecimal amount) {
        auditRepository.save(new AuditLog(fromId, toId, amount));
        // Commits in its OWN transaction, independent of transfer()'s outcome
    }
}
```

Because `logAttempt` uses `REQUIRES_NEW`, it commits on its own even if `transfer()` later throws and rolls everything else back. Without `REQUIRES_NEW`, the audit log would be part of the same transaction and vanish along with the rolled-back transfer.

---

## Isolation — What One Transaction Can See of Another's Uncommitted Changes

Propagation controls nesting. **Isolation** controls visibility between concurrent transactions.

### The three classic read anomalies

**1. Dirty read** — reading a value another transaction wrote but hasn't committed yet.

```
Thread 1: UPDATE wallets SET balance = 1500 WHERE id = 1   (not committed)
Thread 2: SELECT balance FROM wallets WHERE id = 1  → reads 1500

Thread 1: ROLLBACK
→ Thread 2 acted on a balance that never actually existed
```

**2. Non-repeatable read** — reading the same row twice in one transaction, getting different values because another transaction committed a change in between.

```
Thread 1: SELECT balance → 1000
Thread 2: UPDATE balance=700 ... COMMIT
Thread 1: SELECT balance → 700   (different result, same transaction)
```

**3. Phantom read** — the same query returns a *new row* on a second execution because another transaction inserted one in between.

### Isolation levels, loosest to strictest

```java
@Transactional(isolation = Isolation.READ_UNCOMMITTED)
// Allows dirty reads. Rarely used. Fastest, least safe.

@Transactional(isolation = Isolation.READ_COMMITTED)
// Default in PostgreSQL. Prevents dirty reads.
// Non-repeatable reads and phantom reads still possible.

@Transactional(isolation = Isolation.REPEATABLE_READ)
// Prevents dirty reads AND non-repeatable reads.
// Phantom reads still possible on some databases.

@Transactional(isolation = Isolation.SERIALIZABLE)
// Strictest — transactions behave as if run sequentially, one at a time.
// Prevents all three anomalies. Slowest, heaviest locking.
```

**Why MPPS doesn't need SERIALIZABLE:** `findByIdForUpdate()` already applies `PESSIMISTIC_WRITE` — a row-level lock — on wallet rows. That lock already prevents dirty reads and non-repeatable reads on that specific row, regardless of isolation level. Row locking and isolation level solve overlapping but distinct problems; you rarely need both maxed out at once.

**Interview gotcha:** "What isolation level does Spring Boot use by default?" — trick question. Spring's default is `Isolation.DEFAULT`, meaning **whatever the underlying database's default is**. For PostgreSQL, that's `READ_COMMITTED`. Spring doesn't impose its own choice.

---

## The Self-Invocation Trap

This is the one that actually breaks silently in production code.

```java
@Service
public class TransferService {

    public void processTransfer(UUID fromId, UUID toId, BigDecimal amount) {
        validateTransfer(fromId, toId, amount);
        doTransfer(fromId, toId, amount);   // ← call within the SAME class
    }

    @Transactional
    public void doTransfer(UUID fromId, UUID toId, BigDecimal amount) {
        // debit/credit logic
    }
}
```

You'd expect `doTransfer()` to run transactionally. **It won't.**

### Why: proxies only intercept external calls

Spring's `@Transactional` support is built on AOP proxies. External callers get a reference to the proxy, not the raw object:

```
External caller → Proxy.doTransfer() → [transaction starts] → RealObject.doTransfer()
```

But a call from *inside the same class* goes through `this` — the raw, unproxied object — and never touches the proxy:

```
processTransfer() → this.doTransfer() → RealObject.doTransfer()   [NO proxy, NO transaction]
```

This silently disables every proxy-based Spring feature on that call — not just `@Transactional`, but `@Async`, `@Cacheable`, everything AOP-based. No error is thrown. It just quietly doesn't work.

### Fix 1 — split into two beans (cleanest, most common)

```java
@Service
public class TransferValidationService {
    private final TransferExecutionService executionService;

    public void processTransfer(UUID fromId, UUID toId, BigDecimal amount) {
        validateTransfer(fromId, toId, amount);
        executionService.doTransfer(fromId, toId, amount);  // external call → goes through proxy
    }
}

@Service
public class TransferExecutionService {
    @Transactional
    public void doTransfer(UUID fromId, UUID toId, BigDecimal amount) {
        // genuinely transactional now
    }
}
```

### Fix 2 — inject a self-reference (works, but a known code smell)

```java
@Service
public class TransferService {

    @Autowired
    private TransferService self;  // Spring injects the PROXY, not `this`

    public void processTransfer(UUID fromId, UUID toId, BigDecimal amount) {
        validateTransfer(fromId, toId, amount);
        self.doTransfer(fromId, toId, amount);  // goes through the proxy
    }

    @Transactional
    public void doTransfer(UUID fromId, UUID toId, BigDecimal amount) {
        // now transactional
    }
}
```

---

## Interview Gotchas — Summary

- **"Does @Transactional roll back on any exception?"** → No — only unchecked exceptions by default. Checked exceptions commit unless you specify `rollbackFor`.
- **"REQUIRED vs REQUIRES_NEW?"** → REQUIRED joins an existing transaction or creates one if none exists. REQUIRES_NEW always suspends any existing transaction and starts fresh, committing independently.
- **"What's Spring's default isolation level?"** → `Isolation.DEFAULT` — delegates to the database's own default (READ_COMMITTED for PostgreSQL). Spring does not impose its own.
- **"Why did my @Transactional method silently not roll back?"** → Almost certainly self-invocation. Check whether the annotated method was called from within the same class rather than through an injected bean reference.
- **"Does row-level locking replace the need for isolation levels?"** → No, but they overlap. Pessimistic locks protect specific rows you explicitly lock; isolation levels are a database-wide policy for all reads/writes. MPPS relies primarily on row locks for wallet safety and doesn't need SERIALIZABLE as a result.

---

## Quick Summary

- `@Transactional` wraps a method in a commit/rollback boundary using a Spring AOP **proxy**.
- **Default rollback behavior**: only unchecked exceptions trigger rollback. Use `rollbackFor` or make exceptions extend `RuntimeException` to change this.
- **Propagation** controls nesting behavior: `REQUIRED` (default, joins/creates), `REQUIRES_NEW` (always isolated, independent commit), `NESTED` (savepoint within the outer transaction).
- **Isolation** controls visibility between concurrent transactions, guarding against dirty reads, non-repeatable reads, and phantom reads. Levels: `READ_UNCOMMITTED` → `READ_COMMITTED` (Postgres default) → `REPEATABLE_READ` → `SERIALIZABLE`.
- **Self-invocation** (a bean calling its own `@Transactional` method internally) silently bypasses the proxy — no transaction runs, no error is thrown. Fix by splitting into separate beans or injecting a self-reference.

## Code Reference (MPPS)

- `TransferService` / wherever `transfer()` lives — the primary candidate for propagation and self-invocation review
- `WalletRepository.findByIdForUpdate()` — the pessimistic lock that reduces MPPS's reliance on strict isolation levels
- Wherever audit logging exists (or would be added) — natural fit for `REQUIRES_NEW` propagation
