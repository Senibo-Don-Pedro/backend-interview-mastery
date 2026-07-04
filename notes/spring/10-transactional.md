# 10 — @Transactional

## The problem it solves
In a banking transfer, multiple things must happen together:
1. Debit sender's wallet
2. Credit receiver's wallet
3. Mark transaction as SUCCESS

If step 2 fails after step 1 already happened — money has disappeared.
@Transactional guarantees: either ALL steps succeed, or NONE of them happen.
If anything fails, everything rolls back to how it was before.

---

## How @Transactional works under the hood — the proxy
This is the most important thing to understand.

When Spring sees @Transactional on a method, it does NOT modify your class.
Instead it creates a PROXY — a wrapper object that sits in front of your class.

```
What you think happens:
    Caller → TransactionServiceImpl.createTransaction()

What actually happens:
    Caller → TransactionServiceImplPROXY.createTransaction()
                 → opens database transaction
                 → calls your REAL createTransaction()
                 → if success: COMMITS
                 → if RuntimeException: ROLLS BACK
```

The proxy intercepts the call, manages the transaction, then delegates to your
real method. Your code doesn't change — Spring wraps it transparently.

WHY this matters: understanding the proxy is the key to understanding
the self-invocation trap (covered below). Everything flows from this.

---

## Propagation — what happens when @Transactional methods call each other?

Propagation controls what happens to the transaction when one @Transactional
method calls another @Transactional method.

### REQUIRED (default — used everywhere in MPPS)
"Use the existing transaction if there is one. If not, create a new one."

```java
@Transactional // REQUIRED — opens a new transaction (none existed)
public void createTransaction() {
    saveTransaction(); // joins the SAME transaction — no new one opened
}

@Transactional // REQUIRED — joins existing transaction from createTransaction()
public void saveTransaction() {
    // shares the same transaction
    // if THIS fails, EVERYTHING rolls back — including createTransaction()'s work
}
```

Both methods share one transaction. If anything fails anywhere, the whole
thing rolls back. This is the right default for banking — total consistency.

### REQUIRES_NEW
"Always create a brand new transaction. Suspend the existing one."

```java
@Transactional // outer transaction
public void createTransaction() {
    saveTransaction();
    saveAuditLog(); // REQUIRES_NEW — its own separate transaction
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void saveAuditLog() {
    // completely separate transaction
    // if createTransaction() fails and rolls back —
    // this audit log is STILL saved because it committed independently
}
```

Use REQUIRES_NEW when the inner operation must succeed regardless of whether
the outer one succeeds or fails. MPPS audit logs are a perfect example —
you want a record that a transfer was attempted, even if it failed.

### NESTED
"Create a savepoint inside the current transaction. Can rollback to that
savepoint without rolling back everything."

Rarely used. Allows partial rollback within a larger transaction.
Not all databases support it. Stick to REQUIRED and REQUIRES_NEW.

---

## Isolation levels — what can transactions see from each other?

Multiple transactions run at the same time in a live system. Isolation levels
control how much one transaction can see of what another is doing concurrently.

### The problems isolation protects against

| Problem | What it means | Example |
|---|---|---|
| Dirty read | Reading uncommitted data from another transaction | Reading a balance that hasn't been saved yet |
| Non-repeatable read | Reading same row twice, getting different values | Balance was 1000, you read it again, now it's 800 |
| Phantom read | Running same query twice, getting different rows | Query returned 5 transactions, same query now returns 6 |

### The four isolation levels

**READ_UNCOMMITTED**
Can read data that hasn't been committed yet (dirty reads allowed).
Fastest but most dangerous. Never use this in a banking system.
You could read a balance that gets rolled back — acting on fake data.

**READ_COMMITTED (PostgreSQL default — what MPPS uses)**
Only reads committed data. Prevents dirty reads.
If another transaction changes a row but hasn't committed yet,
you see the OLD value until they commit.
Safe enough for most banking operations when combined with locking.

**REPEATABLE_READ**
If you read a row twice in the same transaction, you get the same value.
Prevents dirty reads AND non-repeatable reads.
Useful when you need to read the same data multiple times and trust it hasn't changed.

**SERIALIZABLE**
Transactions run as if they were completely sequential — no overlap at all.
Maximum safety, maximum performance cost. Rarely needed when you have
proper locking strategies in place.

```java
// Explicitly setting isolation level
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void processTransfer() {
    // reading wallet balance twice guaranteed to give same result
}
```

### Why MPPS doesn't need SERIALIZABLE
MPPS uses PostgreSQL's default READ_COMMITTED + PESSIMISTIC_WRITE locks
on wallet rows. The lock prevents concurrent modifications to the same wallet
row during a transaction. You get the safety of SERIALIZABLE on the rows
that matter, without the performance cost of locking everything.

---

## The self-invocation trap — the most important gotcha

Remember: @Transactional works because Spring creates a PROXY around your class.
When someone from OUTSIDE calls your method, it goes through the proxy.
The proxy opens the transaction. Everything works.

But what if you call a @Transactional method from WITHIN THE SAME CLASS?

```java
@Service
public class TransactionServiceImpl {

    @Transactional
    public void createTransaction() {
        // does some work...
        processWallet(); // calling another method in the SAME class
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processWallet() {
        // you EXPECT a new transaction here
        // you are NOT getting one — @Transactional is IGNORED
    }
}
```

Here is exactly what happens:

```
External caller
      ↓
TransactionServiceImplPROXY   ← proxy intercepts this call ✓
      ↓
TransactionServiceImpl.createTransaction()   ← running inside proxy
      ↓
this.processWallet()   ← calls on THIS (the real object), bypasses proxy ✗
```

When createTransaction() calls processWallet(), it calls it on `this` —
the real object, not the proxy. The proxy is bypassed completely.
The @Transactional annotation on processWallet() is silently IGNORED.
No new transaction is opened. processWallet() just joins createTransaction()'s transaction.

This is one of the most common silent bugs in Spring applications.
No error. No warning. It just doesn't work the way you expect.

### How to fix it

**Option 1 — move the method to a different class (preferred)**
```java
@Service
public class WalletService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processWallet() {
        // now called from outside — goes through WalletService proxy — works!
    }
}

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl {
    private final WalletService walletService;

    @Transactional
    public void createTransaction() {
        walletService.processWallet(); // external call → proxy works correctly ✓
    }
}
```

**Option 2 — inject self (hacky, avoid if possible)**
```java
@Service
public class TransactionServiceImpl {
    @Autowired
    private TransactionServiceImpl self; // inject the PROXY of yourself

    @Transactional
    public void createTransaction() {
        self.processWallet(); // goes through proxy ✓ — but this is ugly
    }
}
```
Always prefer Option 1. Injecting self is a code smell and confuses readers.

### How MPPS avoids this
TransactionServiceImpl.createTransaction() and TransactionWorker.processTransaction()
are in SEPARATE classes. This is why — so each class's proxy works independently
and transactional boundaries stay clean. This is the correct design.

Inside TransactionWorker.processTransaction() (@Transactional), it calls
handleCredit(), handleDebit(), handleTransfer() — all in the same class.
If those had @Transactional annotations, they would be ignored.
They don't have @Transactional on them for exactly this reason.

---

## When does Spring roll back?

By default, Spring ONLY rolls back on unchecked exceptions (RuntimeException).
It does NOT roll back on checked exceptions by default.

```java
@Transactional
public void createTransaction() throws IOException {
    // if IOException is thrown — NO ROLLBACK (checked exception)
    // if RuntimeException is thrown — ROLLBACK ✓
}

// To rollback on checked exceptions too:
@Transactional(rollbackFor = Exception.class)
public void createTransaction() throws IOException {
    // now rolls back on ANY exception
}
```

This is another reason Spring prefers unchecked exceptions — automatic rollback
without needing to configure rollbackFor every time.

---

## Quick reference

```java
// Default — joins existing transaction or creates new one
@Transactional

// Always create new transaction (audit logs, independent operations)
@Transactional(propagation = Propagation.REQUIRES_NEW)

// Explicit isolation level
@Transactional(isolation = Isolation.REPEATABLE_READ)

// Rollback on checked exceptions too
@Transactional(rollbackFor = Exception.class)

// Read-only hint (performance optimisation for queries)
@Transactional(readOnly = true)
```

---

## Interview answer for self-invocation trap
"@Transactional works via a Spring proxy. When a method is called from outside
the class, the proxy intercepts it and manages the transaction. But when a method
calls another method in the same class, it calls on 'this' — the real object —
bypassing the proxy entirely. The @Transactional annotation on the inner method
is silently ignored. The fix is to extract the inner method to a separate class
so the call goes through a proxy."

---

## Quick summary
- @Transactional works via a Spring proxy — the proxy manages open/commit/rollback
- REQUIRED (default) = join existing transaction or create new one
- REQUIRES_NEW = always create new transaction, suspend existing one
- READ_COMMITTED (PostgreSQL default) = only read committed data
- Self-invocation trap = calling @Transactional from same class bypasses proxy, annotation ignored
- Spring only rolls back on RuntimeException by default — not checked exceptions

## Code reference
See: MPPS — TransactionServiceImpl.java (createTransaction — @Transactional)
See: MPPS — TransactionWorker.java (processTransaction — @Transactional)
See: MPPS — AuditAspect.java (audit logging — candidate for REQUIRES_NEW)
