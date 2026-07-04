# 09 — Bean Lifecycle and Scopes

## The full lifecycle
```
1. Spring finds class (@Service, @Component etc)
2. Spring calls constructor
3. Spring injects dependencies
4. @PostConstruct runs (setup code)
5. Bean is ready — serves requests
6. App shuts down...
7. @PreDestroy runs (cleanup code)
8. Bean is destroyed
```

## @PostConstruct
Runs AFTER constructor AND after all dependencies are injected.
Use for: validating config on startup, loading cache, verifying connections.

```java
@PostConstruct
public void init() {
    // dependencies are guaranteed to be available here
    System.out.println("Bean is ready");
}
```

## @PreDestroy
Runs just before the bean is destroyed (app shutdown).
Use for: closing connections, flushing caches, releasing resources.

```java
@PreDestroy
public void cleanup() {
    System.out.println("Bean shutting down — cleaning up");
}
```

## Bean Scopes

| Scope | Instances | When created |
|---|---|---|
| Singleton (default) | 1 total | App startup |
| Prototype | 1 per injection | Every time requested |
| Request | 1 per HTTP request | Each request |
| Session | 1 per user session | Each session |

```java
@Service                    // singleton — default, no annotation needed
@Scope("prototype")         // new instance every time
@Scope("request")           // new instance per HTTP request
@Scope("session")           // new instance per user session
```

## Prototype inside singleton — the trap
```java
@Service // singleton
public class TransactionServiceImpl {

    @Autowired
    private ReportGenerator reportGenerator; // prototype bean

    // You'd expect a new ReportGenerator every time this service uses it.
    // WRONG — TransactionServiceImpl is created once at startup.
    // Spring injects ONE ReportGenerator at that moment and it never changes.
    // The prototype scope is completely ignored here.
}
```
Fix: inject ApplicationContext and call getBean() manually each time — but this is rare.

## Interview gotchas

**1. Singleton beans must be stateless**
One instance is shared by all requests. If you store request-specific data
in a field, concurrent requests will overwrite each other. Silent bug.

**2. Prototype inside singleton**
Prototype bean injected into singleton only gets created ONCE — at the moment
the singleton is created. It stays there forever. Prototype scope is ignored.

**3. All MPPS beans are singleton**
TransactionServiceImpl, WalletRepository, TransactionWorker — all singleton.
You can tell because none of them have @Scope annotation.

## Quick summary
- @PostConstruct = setup after injection, before serving requests
- @PreDestroy = cleanup before bean is destroyed
- Singleton = one instance shared everywhere (default, must be stateless)
- Prototype inside singleton = gotcha, prototype only created once

## Code reference
See: MPPS — TransactionServiceImpl.java, ApplicationConfig.java
