# Circular Dependencies in Spring

## The Problem This Solves (Understanding, Not "Solving")

Imagine two services that each need the other:

```java
@Service
public class TransferService {
    private final NotificationService notificationService;

    public TransferService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
}

@Service
public class NotificationService {
    private final TransferService transferService;

    public NotificationService(TransferService transferService) {
        this.transferService = transferService;
    }
}
```

With **constructor injection**, Spring must fully construct an object before handing it to anything else. But here:

- To build `TransferService`, Spring needs a complete `NotificationService` first.
- To build `NotificationService`, Spring needs a complete `TransferService` first.

Neither can be built first. There's no valid construction order. Spring detects this at startup and fails fast:

```
BeanCurrentlyInCreationException: Error creating bean with name 'transferService':
Requested bean is currently in creation: Is there an unresolvable circular reference?
```

---

## Why Constructor Injection Can't Fix This — And Why That's a Good Thing

Constructor injection requires the *entire object* to exist before it's passed anywhere. There's no partial/half-built version of `TransferService` that Spring can hand to `NotificationService`'s constructor while `TransferService` itself is still under construction. It's a genuine chicken-and-egg problem — Spring refuses to guess or fake it.

This failure is a **feature, not a bug.** A circular dependency between two services is almost always a sign of a design problem — two classes are too tightly coupled and should either be merged, or have their shared responsibility extracted into a third class. Spring failing loudly at startup surfaces that design smell immediately, rather than letting a shaky object graph run silently in production.

---

## The Band-Aid Fix: `@Lazy`

When the circular dependency is unavoidable short-term (legacy code, tight deadline), Spring provides an escape hatch:

```java
@Service
public class TransferService {
    private final NotificationService notificationService;

    public TransferService(@Lazy NotificationService notificationService) {
        this.notificationService = notificationService;
    }
}
```

**What `@Lazy` actually does:** instead of resolving the real `NotificationService` bean immediately, Spring injects a **proxy** — a stand-in object. The real bean is only created the first time a method is actually called on that proxy. This breaks the deadlock because, at construction time, Spring hands over a placeholder rather than needing the fully-built real object.

**Important:** this fixes the *symptom* (app fails to start), not the *cause* (the two services are still tightly coupled to each other). Treat `@Lazy` here as a temporary patch, not an architectural decision.

---

## The Real Fixes: Break the Cycle

### Fix 1 — Extract shared logic into a third service

```java
@Service
public class TransferService {
    private final AuditService auditService;
    // no longer depends on NotificationService directly
}

@Service
public class NotificationService {
    private final AuditService auditService;
    // no longer depends on TransferService directly
}

@Service
public class AuditService {
    // shared logic both needed — nothing is circular anymore
}
```

### Fix 2 — Use events instead of direct method calls (preferred by most senior engineers)

`TransferService` publishes an event. `NotificationService` listens for it. Neither needs a direct reference to the other at all:

```java
@Service
public class TransferService {
    private final ApplicationEventPublisher eventPublisher;

    public void transfer(UUID fromId, UUID toId, BigDecimal amount) {
        // ... transfer logic ...
        eventPublisher.publishEvent(new TransferCompletedEvent(fromId, toId, amount));
    }
}

@Service
public class NotificationService {
    @EventListener
    public void onTransferCompleted(TransferCompletedEvent event) {
        // send notification
    }
}
```

This fully decouples the two services — worth mentioning in an interview even if not asked directly about circular dependencies, since it shows architectural instinct beyond just "add `@Lazy`."

---

## Interview Gotcha: Why Doesn't Field Injection Throw the Same Exception?

```java
@Service
public class TransferService {
    @Autowired
    private NotificationService notificationService;  // field injection
}
```

With **field injection**, Spring can:
1. Create a half-built `TransferService` using its no-arg constructor
2. Register it in the container as "currently being created"
3. Move on to build `NotificationService`
4. Come back afterward and set the field via reflection

This is why field injection "just works" with circular dependencies where constructor injection throws.

**This is a trap, not an advantage.** It's one of the most cited reasons constructor injection is the recommended default in Spring: it makes circular dependencies — a genuine design smell — visible immediately at startup, instead of silently allowing a shaky, half-initialized object graph to run in production. Field injection hides the problem; it doesn't solve it.

---

## Interview Gotchas — Summary

- **"Why does Spring throw on circular constructor dependencies?"** → Constructor injection requires a fully-built object before it can be passed anywhere. Two services needing each other's complete instance at construction time is unresolvable — there's no valid build order.
- **"What does `@Lazy` do, mechanically?"** → Injects a proxy instead of the real bean. The real bean is only instantiated on first actual method call through that proxy, which breaks the circular construction requirement.
- **"Why doesn't field injection throw `BeanCurrentlyInCreationException`?"** → Spring can create a bean via its no-arg constructor, register it as "in creation," and inject fields via reflection afterward — sidestepping the need for a fully complete dependency at construction time.
- **"Is `@Lazy` a real fix?"** → No — it resolves the startup crash but leaves the underlying tight coupling in place. Treat it as a stopgap, not a design decision.
- **"What are the real fixes?"** → Extract shared logic into a third service, or decouple via `ApplicationEventPublisher` / `@EventListener` so neither service needs a direct reference to the other.

---

## Quick Summary

- A **circular dependency** is two (or more) beans each requiring the other to exist before they themselves can be constructed.
- **Constructor injection** cannot resolve this — Spring fails fast with `BeanCurrentlyInCreationException` at startup, which is intentional: it surfaces a design smell immediately.
- **`@Lazy`** breaks the deadlock by injecting a proxy instead of the real bean, deferring actual creation until first use — a workaround, not a fix.
- **Field injection** avoids the exception entirely because Spring can build a bean, register it as in-progress, and wire fields in afterward via reflection — but this hides rather than solves the underlying coupling problem, which is why constructor injection is the recommended default.
- **Real fixes**: extract shared logic into a third service, or decouple the two services using Spring events (`ApplicationEventPublisher` + `@EventListener`).

## Code Reference (MPPS)

- Any two services in MPPS that might reference each other directly (e.g. a hypothetical `TransferService` ↔ `NotificationService` relationship) are candidates to review for this pattern.
- `ApplicationEventPublisher` / `@EventListener` — not yet used in MPPS, but a natural extension point if notification or audit logic is added later without introducing tight coupling.
