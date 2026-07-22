# IoC Container and Dependency Injection — Full Notes

## The Problem This Solves

Imagine `TransactionServiceImpl` needs a `TransactionRepository` and a `WalletRepository` to do its job. The naive approach: create those dependencies yourself, right inside the class that needs them:

```java
public class TransactionServiceImpl {
    private TransactionRepository transactionRepository = new TransactionRepositoryImpl();
    private WalletRepository walletRepository = new WalletRepositoryImpl();
    // ...
}
```

This seems fine until you actually try to use or test this class. `TransactionServiceImpl` is now **hard-wired** to one specific concrete implementation of each dependency. Want to swap in a mock `WalletRepository` for a unit test? You can't, without changing this class's source code. Want to share a single `WalletRepository` instance across ten different services instead of creating ten separate ones? You'd have to manually pass it around everywhere yourself, or resort to global singletons/static state — both messy.

**Inversion of Control (IoC)** is the architectural principle that flips who's responsible for creating and wiring dependencies: instead of a class creating its own dependencies, **something else** (a framework, in Spring's case) creates them and hands them over. The class receiving the dependency doesn't control how or when that dependency was constructed — control has been "inverted" away from it.

**Dependency Injection (DI)** is the specific *mechanism* Spring uses to actually carry out IoC — it's how Spring hands a class its dependencies (via the constructor, typically), rather than the class reaching out and constructing them itself.

---

## The `ApplicationContext` — Spring's Object Factory

The `ApplicationContext` is Spring's central object factory — a "warehouse" holding every object (called a **bean**) that Spring manages. It's built once, at application startup.

```
ApplicationContext
├── TransactionServiceImpl  (one instance)
├── WalletRepository        (one instance)
├── IdempotencyServiceImpl  (one instance)
└── ... every Spring-managed object in your app
```

At startup, Spring scans your codebase for classes annotated in specific ways (`@Component`, `@Service`, `@Repository`, `@Controller`), constructs one instance of each, wires up whatever dependencies each one needs, and stores the finished object in this warehouse. From that point on, whenever any part of your app needs one of these objects, Spring hands out a reference to the **already-constructed instance** sitting in the `ApplicationContext` — nobody manually calls `new SomeService()` anywhere in application code.

---

## How DI Actually Works in Your MPPS Code

```java
@Service
@RequiredArgsConstructor          // Lombok annotation — auto-generates a constructor
                                    // that takes every `final` field as a parameter
public class TransactionServiceImpl {
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
}
```

Here's what's actually happening, step by step, at application startup:

1. Spring finds `TransactionServiceImpl` during its classpath scan (because of `@Service`).
2. Lombok, at compile time, has already generated a constructor that looks like:
   ```java
   public TransactionServiceImpl(TransactionRepository transactionRepository, WalletRepository walletRepository) {
       this.transactionRepository = transactionRepository;
       this.walletRepository = walletRepository;
   }
   ```
3. Spring inspects that constructor's parameter types (`TransactionRepository`, `WalletRepository`).
4. Spring looks inside the `ApplicationContext` for a bean matching each of those types.
5. Spring calls the constructor itself, passing in the beans it found, and stores the resulting `TransactionServiceImpl` instance as a bean of its own.

**You never write `new TransactionServiceImpl(...)` anywhere in your own code.** Spring does it, once, at startup, and every subsequent piece of code that needs a `TransactionServiceImpl` just gets handed the same already-built instance.

---

## How Spring Knows What to Manage — The Stereotype Annotations

| Annotation | What it signals |
|---|---|
| `@Component` | A generic Spring-managed class — use this when none of the more specific ones below fit |
| `@Service` | This class holds business/service-layer logic |
| `@Repository` | This class is a data-access layer class (and gets a bonus — see below) |
| `@Controller` | This class is a web layer class handling HTTP requests |

**Mechanically, all four annotations do the exact same thing** — they mark the class to be discovered by Spring's classpath scanning and registered as a bean. The difference between them is purely **semantic/documentary**: they communicate *intent* — what role this class plays in the architecture — to both other developers reading the code and to certain Spring features that specifically look for one of these annotations (like `@Repository`'s automatic database exception translation, covered in the next topic).

---

## Constructor Injection vs Field Injection

```java
// Field injection — BAD, avoid this
@Autowired
private WalletRepository walletRepository;
// The dependency is a hidden, invisible requirement — you can't tell from the constructor
// that this class needs a WalletRepository at all. The field also can't be `final`,
// meaning it's technically reassignable after construction, even though it never should be.

// Constructor injection — GOOD (what MPPS actually uses, via @RequiredArgsConstructor)
private final WalletRepository walletRepository;
// The dependency is explicit and visible right in the constructor signature.
// Making it `final` guarantees it's set exactly once, at construction, and never changes again.
// You can also construct this object directly in a unit test, passing in a mock,
// with ZERO Spring involvement required — just call `new TransactionServiceImpl(mockRepo, ...)`.
```

**Why constructor injection is strongly preferred:**
- **Explicitness** — every dependency a class needs is visible in one place: its constructor signature. You don't have to scan the whole class body hunting for `@Autowired` fields to understand what it depends on.
- **Immutability** — `final` fields can only be assigned once, which matches the reality that dependencies shouldn't change after a bean is constructed.
- **Testability** — you can instantiate the class directly in a plain unit test (`new TransactionServiceImpl(mockTransactionRepo, mockWalletRepo)`), passing in test doubles, without needing to spin up any part of the Spring framework at all.

---

## Interview Answer (Worth Memorizing Nearly Verbatim)

"The `ApplicationContext` is Spring's object factory. At startup it scans for annotated classes, creates singleton instances, registers them as beans, and uses constructor injection to wire dependencies automatically. IoC inverts who controls object creation — from your own code, to the framework."

---

## Interview Gotchas

- **IoC is the principle; DI is the mechanism.** Don't conflate them when explaining — IoC is the *what* (control over object creation is inverted, away from your classes), DI is the *how* (Spring specifically achieves that inversion by injecting dependencies, typically via constructors).
- **`@Component`, `@Service`, `@Repository`, `@Controller` all register a bean identically** — the differences are semantic, except for `@Repository`'s extra exception-translation behavior.
- **Constructor injection over field injection is a near-universal Spring best practice** for the explicitness, immutability, and testability reasons above — and, as covered in the circular dependencies notes, it also has the added benefit of surfacing genuine circular-dependency design problems at startup, rather than silently hiding them.

## Quick Summary

- **IoC** = the framework, not your own code, controls the creation and lifecycle of your objects.
- **DI** = the specific mechanism Spring uses to hand a class its dependencies (typically via the constructor) instead of the class constructing them itself.
- **`ApplicationContext`** = the warehouse holding every Spring-managed bean, built once at startup.
- **`@Component`/`@Service`/`@Repository`/`@Controller`** all register a class as a bean identically — the difference is purely semantic intent (with one bonus for `@Repository`).
- **Always prefer constructor injection** (`final` fields + `@RequiredArgsConstructor` in MPPS's case) over field injection, for explicitness, immutability, and testability.

## Code Reference

See MPPS — `TransactionServiceImpl.java`, `TransactionWorker.java` — both demonstrate constructor injection via `@RequiredArgsConstructor` and `final` fields, with dependencies wired automatically by Spring at startup.
