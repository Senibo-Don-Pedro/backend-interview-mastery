# Bean Lifecycle and Scopes — Full Notes

## The Problem This Solves

Once Spring decides to manage an object as a bean, that object still needs to go through a series of well-defined stages — it has to actually get built, have its dependencies wired in, potentially run some setup logic before it's safe to use, serve requests for however long the application runs, and eventually get cleaned up when the application shuts down. Without a defined lifecycle, there'd be no reliable place to put "run this once everything's ready" logic (like validating a config value or warming a cache) or "run this right before shutdown" logic (like closing a database connection cleanly). Spring's bean lifecycle gives you exact, guaranteed hook points for both.

Separately: not every bean should necessarily be a single shared instance for the whole application. Sometimes you need a fresh instance per HTTP request, or per user session. **Scopes** control exactly how many instances of a given bean exist, and when new ones get created.

---

## The Full Lifecycle, Step by Step

```
1. Spring finds the class (via @Service, @Component, etc., or a @Bean method)
2. Spring calls the constructor
3. Spring injects the bean's dependencies (via that constructor, per the DI notes)
4. @PostConstruct method runs (your custom setup code, if you've defined any)
5. Bean is fully ready — it serves requests for the rest of the application's lifetime
6. ... application shuts down ...
7. @PreDestroy method runs (your custom cleanup code, if you've defined any)
8. Bean is destroyed
```

The key thing to internalize: steps 2 and 3 (construction and dependency injection) are **not** the same moment as "this bean is fully ready to be used." There's a real gap between "constructed" and "ready," and `@PostConstruct` exists specifically to let you do work in that gap, with the guarantee that every dependency is already available by the time your code runs.

---

## `@PostConstruct`

Runs **after** the constructor has completed **and** after every dependency has already been injected. This ordering guarantee is the entire point of the annotation.

```java
@PostConstruct
public void init() {
    // By the time this method runs, every dependency this bean needs
    // is GUARANTEED to already be set and ready to use.
    System.out.println("Bean is ready");
}
```

**Use cases:**
- **Validating configuration on startup** — e.g., checking that a required property was actually set, and failing fast at application startup rather than discovering the problem later, mid-request, in production.
- **Loading a cache** — populating an in-memory cache from the database once, right when the bean becomes ready, rather than lazily on the first real request (which would make the very first user experience a slow one).
- **Verifying connections** — confirming a connection to an external service is actually reachable before the application starts accepting real traffic.

**Why this couldn't just go in the constructor:** at the exact moment the constructor is running, dependency injection for *that specific bean* may not have fully completed depending on injection mechanism and ordering — and more importantly, using the constructor for setup logic mixes "how do I get built" with "what do I do once I'm ready," which are conceptually different responsibilities. `@PostConstruct` cleanly separates them.

---

## `@PreDestroy`

Runs just before the bean is destroyed — typically as part of an orderly application shutdown.

```java
@PreDestroy
public void cleanup() {
    System.out.println("Bean shutting down — cleaning up");
}
```

**Use cases:**
- Closing open connections (database connections, message queue connections) cleanly, rather than leaving them dangling.
- Flushing any in-memory caches or buffers to disk/database before the data disappears with the shutting-down JVM.
- Releasing any other held resources (file handles, thread pools) gracefully.

---

## Bean Scopes

Scope controls **how many instances** of a bean exist, and **when** a new one gets created.

| Scope | Instances | Created |
|---|---|---|
| **Singleton** (default) | Exactly 1, for the entire application | At app startup |
| **Prototype** | A new one every single time the bean is requested/injected | Each time it's requested |
| **Request** | 1 per HTTP request | At the start of each incoming HTTP request |
| **Session** | 1 per user session | At the start of each new user session |

```java
@Service                      // singleton — the default; no annotation needed to get this behavior
@Scope("prototype")           // a brand new instance every time this bean is requested
@Scope("request")             // a new instance for every incoming HTTP request
@Scope("session")             // a new instance for every distinct user session
```

**Singleton is the default for a reason:** most beans (services, repositories) are naturally stateless and safely shareable — there's no reason to pay the cost of constructing a new `TransactionServiceImpl` for every single request when one shared instance works perfectly fine, provided it holds no per-request state (see the gotcha below).

---

## The Prototype-Inside-Singleton Trap

This is one of the most commonly-tested gotchas in Spring interviews, precisely because it's genuinely counter-intuitive the first time you encounter it.

```java
@Service  // singleton — created exactly ONCE, at application startup
public class TransactionServiceImpl {

    @Autowired
    private ReportGenerator reportGenerator;  // this bean is @Scope("prototype")

    // Naive expectation: "since ReportGenerator is prototype-scoped,
    // I should get a fresh ReportGenerator instance every time this service uses it."
    //
    // WRONG. Here's what actually happens:
}
```

**What actually happens:** `TransactionServiceImpl` itself is a singleton — meaning Spring constructs it exactly **once**, at application startup. At that single moment of construction, Spring resolves the `@Autowired` dependency for `reportGenerator` **one time** and injects **one specific instance** of `ReportGenerator` into that field. From that point forward, `TransactionServiceImpl` holds onto that exact same `ReportGenerator` instance forever — because the field itself was only ever set once, during the singleton's one-time construction.

**The prototype scope on `ReportGenerator` is completely nullified in this scenario.** "Prototype" means "a new instance every time it's *requested from Spring*" — but `TransactionServiceImpl` only ever *requests* it once (implicitly, via field injection at construction time), so it only ever gets one instance, despite the prototype annotation technically being correct and present on `ReportGenerator` itself.

**The (rarely-needed) fix:** inject the `ApplicationContext` itself into the singleton, and manually call `getBean()` on it every time you actually need a fresh prototype instance — this forces Spring to genuinely re-resolve the dependency on each call, rather than relying on a field that was only ever set once:

```java
@Service
public class TransactionServiceImpl {

    @Autowired
    private ApplicationContext applicationContext;

    public void generateReport() {
        ReportGenerator freshGenerator = applicationContext.getBean(ReportGenerator.class);
        // NOW you genuinely get a new instance each time this method runs
    }
}
```

This pattern is rare in practice specifically because it's a bit of a code smell — needing a fresh prototype instance repeatedly from within a singleton often signals the design itself might benefit from rethinking, but it's a real, valid fix when genuinely needed.

---

## Interview Gotchas — Summary

**1. Singleton beans must be stateless.**
Because exactly one instance is shared across every single request/thread in the entire application, storing any request-specific data in an instance field is dangerous — concurrent requests hitting the same singleton simultaneously will read and overwrite each other's data through that shared field, with zero errors thrown and completely silent, intermittent, hard-to-reproduce data corruption as the result.

```java
@Service  // singleton — DON'T do this
public class BadTransactionService {
    private String currentUserId;  // ⚠️ shared mutable state across every concurrent request

    public void processTransaction(String userId) {
        this.currentUserId = userId;  // Thread A sets this...
        // ... Thread B, running concurrently, sets it to a DIFFERENT userId here ...
        doSomethingWith(currentUserId);  // Thread A might now read Thread B's userId instead of its own
    }
}
```

**2. Prototype-inside-singleton is a trap, not a feature.** Covered in full above — the prototype bean only gets constructed once, at the moment the enclosing singleton is itself constructed, and then stays frozen in that field forever.

**3. All of MPPS's beans are singleton.** `TransactionServiceImpl`, `WalletRepository`, `TransactionWorker` — every one of them. You can confirm this simply by checking that none of them carry an explicit `@Scope` annotation; singleton is what you get by not specifying anything at all.

---

## Quick Summary

- **`@PostConstruct`** runs after construction *and* after all dependencies are injected — the correct place for setup logic that needs guaranteed-available dependencies (config validation, cache warming, connection checks).
- **`@PreDestroy`** runs just before the bean is destroyed — the correct place for cleanup (closing connections, flushing buffers).
- **Singleton** (the default) = exactly one shared instance for the whole application; must be stateless to be safe under concurrent access.
- **Prototype** = a new instance every time the bean is genuinely requested from Spring — but injecting a prototype bean into a singleton via a field only resolves it **once**, at the singleton's construction time, effectively nullifying the prototype behavior. The fix (rarely needed) is manually calling `applicationContext.getBean()` each time a fresh instance is actually required.
- **Request/session scopes** create one instance per HTTP request or per user session, respectively.

## Code Reference

See MPPS — `TransactionServiceImpl.java`, `ApplicationConfig.java`. Every bean in MPPS is singleton by default (no `@Scope` annotation present anywhere), which is appropriate given that the service and repository layers hold no per-request mutable state.
