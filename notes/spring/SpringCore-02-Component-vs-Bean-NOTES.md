# @Component vs @Bean vs @Configuration — Full Notes

## The Core Problem

Spring needs to know which classes should become beans in the `ApplicationContext`. There are two fundamentally different mechanisms for registering a bean, and which one applies depends entirely on one question: **do you own the source code of the class being registered?**

---

## Way 1 — `@Component` (and Its Variants)

This annotation goes directly on a **class you wrote**. Spring discovers it automatically at startup by scanning your packages for annotated classes — this process is called **classpath scanning**.

```java
@Component
public class SomeHelper { ... }
```

Spring finds this class during its startup scan, constructs one instance, and stores it in the `ApplicationContext`. That's the entire mechanism — no extra step required.

### The Variants — Semantically Different, Mechanically Identical

```java
@Component       // generic — use when none of the below fit
@Service         // signals: this is business/service-layer logic
@Repository      // signals: this is a data-access class — AND gets a real behavioral bonus (below)
@Controller      // signals: this is a web-layer class
@RestController  // @Controller + @ResponseBody combined — signals a REST API controller
```

All four register the class as a bean in exactly the same way. The difference is about communicating **role and intent** — both to other developers reading your code, and, in `@Repository`'s case, to Spring itself.

### `@Repository` Gets Something Extra — Exception Translation

This is the one variant with genuinely different runtime behavior, not just a naming convention. Spring wraps the database-specific exceptions your persistence layer might throw (Hibernate-specific exceptions, JDBC `SQLException` variants, etc.) into Spring's own unified exception hierarchy (`DataAccessException` and its subclasses).

**Why this matters practically:** without `@Repository`'s translation, your service layer would need to know about and catch persistence-technology-specific exceptions (a Hibernate exception type, say) — meaning if you ever swapped the underlying persistence technology, every service catching those specific exception types would need to change too. With `@Repository`, your service layer only ever needs to catch Spring's own `DataAccessException`, regardless of what's actually running underneath (Hibernate, plain JDBC, etc.). This decouples your business logic from your specific persistence implementation.

### MPPS Examples

- `@Service TransactionServiceImpl` — business logic layer
- `@Service IdempotencyServiceImpl` — business logic layer
- `@RestController TransactionController` — web layer
- Spring Data repository interfaces are auto-registered by Spring Data itself — no explicit `@Repository` annotation is even required on them, since Spring Data generates the implementation and registers it as a bean automatically.

### The Rule for `@Component`

Use `@Component` (or one of its variants) when you **wrote** the class and can put the annotation directly onto it.

---

## Way 2 — `@Bean` Inside `@Configuration`

### The Problem `@Component` Genuinely Cannot Solve

What happens when you need Spring to manage a class from a **third-party library** — one whose source code you didn't write and can't edit? You can't add `@Component` to a class inside someone else's JAR file. There's no source file of theirs sitting in your project for you to annotate.

Concrete examples: `BCryptPasswordEncoder` comes from Spring Security. `RestTemplate` comes from Spring Web. `ObjectMapper` comes from the Jackson library. You need Spring to manage instances of these — inject them wherever needed, control their lifecycle — but you have zero ability to put an annotation on their source code, because you don't own it.

### The Solution

```java
@Configuration                          // tells Spring: "this class contains bean definitions"
public class ApplicationConfig {

    @Bean                                // tells Spring: "run this method, and register whatever it returns as a bean"
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}
```

This is precisely what MPPS's `ApplicationConfig.java` does. Spring calls `passwordEncoder()` once, at startup, receives the `BCryptPasswordEncoder` instance it returns, and stores that instance as a bean — even though `BCryptPasswordEncoder`'s own source code has no `@Component` anywhere on it, and never will, because it's not your class to annotate. From this point forward, anywhere in MPPS that needs a `BCryptPasswordEncoder` (like the `UserService` from the hashing/JWT notes), Spring injects this bean automatically, exactly as if it had been discovered via classpath scanning.

### `@Configuration` and `@Bean` — They Work Together, Not Independently

- **`@Configuration`** goes on the **class** — declaring "this class is a source of bean definitions."
- **`@Bean`** goes on **methods inside that class** — declaring "run this method, and register its return value as a bean."
- You technically *can* use `@Bean` on a method inside a plain class without `@Configuration`, but doing so loses important underlying proxy behavior that ensures, among other things, that calling one `@Bean` method from inside another `@Bean` method in the same class correctly returns the same singleton instance rather than constructing a fresh one. **Always pair them together** — don't rely on the edge-case behavior of skipping `@Configuration`.

### The Rule for `@Bean`

Use `@Bean` when you need to register something you **don't own** the source code of, or when you need fine-grained control over exactly how an object gets constructed (custom constructor arguments, conditional logic, complex setup that can't just be expressed via annotations on a class you own).

---

## The Interview Question That Actually Trips People Up

**Interviewer:** "So are you saying `@Component` classes can't be manually configured?"

**The wrong answer:** "Components are automatically managed and beans are manually managed" — this phrasing implies `@Component` classes are somehow locked into pure auto-configuration with no room for customization, which is false.

**The correct answer:** "`@Component` classes absolutely *can* be manually configured. The real distinction between `@Component` and `@Bean` is about **who owns the class** — not about manual versus automatic configuration."

### How You Actually Manually Configure `@Component` Classes

```java
// @Qualifier — when multiple beans of the SAME TYPE exist, specify exactly which one to inject
@Service
@Qualifier("primaryTransactionService")
public class TransactionServiceImpl implements TransactionService { ... }

// @Primary — mark this bean as the default choice whenever multiple candidates of its type exist
@Service
@Primary
public class TransactionServiceImpl implements TransactionService { ... }

// @Scope — override the default singleton scope (covered fully in the Bean Lifecycle notes)
@Service
@Scope("prototype")
public class ReportGenerator { ... }

// @Lazy — defer creation of this bean until it's actually first needed, rather than at startup
@Service
@Lazy
public class HeavyReportingService { ... }
```

Every one of these is a form of manual configuration applied directly to an auto-discovered `@Component` class. The claim that components are somehow "automatic-only" and beans are "manual-only" simply doesn't hold up — both mechanisms support manual customization; they just differ in *how Spring discovers the class in the first place* (scanning vs an explicit factory method).

---

## Side-by-Side Comparison

| | `@Component` | `@Bean` |
|---|---|---|
| Goes on | A CLASS | A METHOD |
| Who owns the class | You | A third-party library |
| How Spring finds it | Classpath scanning | Explicitly called by Spring, via the `@Configuration` class |
| Lives inside | Anywhere in a scanned package | A `@Configuration` class |
| Can be manually configured | YES — `@Qualifier`, `@Primary`, `@Scope`, `@Lazy` | YES — via constructor arguments, conditional logic inside the method body |
| MPPS example | `TransactionServiceImpl` | `BCryptPasswordEncoder` |

---

## Common Mistake to Avoid

Using `@Component` on a class when what you actually need is custom construction logic — specific constructor arguments computed at runtime, conditional setup, or anything beyond "just instantiate this with its default constructor and inject its dependencies." `@Component` is for simple, auto-detectable classes. `@Bean` is for whenever you need real control over the construction process itself.

---

## Interview Answer (Worth Memorizing Nearly Verbatim)

"There are two ways to register a bean in Spring. `@Component` goes on a class you own, and Spring auto-detects it via classpath scanning. `@Bean` goes on a method inside a `@Configuration` class, and is used for third-party classes you can't annotate directly, or when you need fine control over construction. Both can be manually configured — the real difference is about who owns the class, not about manual versus automatic configuration."

---

## Interview Gotchas — Summary

- **The distinction is ownership, not "automatic vs manual."** This is the single most commonly misunderstood point about this topic — don't fall into the trap of framing it that way.
- **`@Repository` isn't just a naming convention** — it genuinely changes runtime behavior via exception translation, decoupling your service layer from persistence-specific exception types.
- **`@Bean` methods should almost always live inside a `@Configuration`-annotated class** — skipping `@Configuration` loses important proxy guarantees around singleton behavior when `@Bean` methods call each other.
- **`@Component` classes support the exact same level of manual customization as `@Bean`-registered ones** — `@Qualifier`, `@Primary`, `@Scope`, and `@Lazy` all apply directly to `@Component` classes.

## Quick Summary

- **`@Component`** (and `@Service`/`@Repository`/`@Controller`/`@RestController`) = goes on a class you own, discovered via classpath scanning.
- **`@Bean`** = goes on a method inside a `@Configuration` class, used to register classes you don't own (or need custom construction logic for).
- **`@Configuration`** marks a class as a container of `@Bean` method definitions — always pair it with `@Bean`, don't rely on skipping it.
- **`@Repository`** carries a genuine behavioral bonus beyond naming: automatic exception translation into Spring's `DataAccessException` hierarchy.
- The real distinction between `@Component` and `@Bean` is **who owns the class** — both support manual configuration equally well.

## Code Reference

See MPPS — `ApplicationConfig.java` (the `@Bean` examples: `BCryptPasswordEncoder`, `RestTemplate`, `DateTimeProvider`) and `TransactionServiceImpl.java` (the `@Service`/`@Component`-style example).
