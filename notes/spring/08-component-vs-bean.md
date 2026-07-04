# 08 — @Component vs @Bean vs @Configuration

## The core problem
Spring needs to know which classes to manage. There are two completely different
ways to register a bean — and which one you use depends on whether you OWN the class.

---

## Way 1 — @Component (and its variants)

Goes directly on a CLASS you wrote. Spring discovers it automatically at startup
by scanning your packages. This is called classpath scanning.

```java
@Component
public class SomeHelper { ... }
```

Spring finds it, creates one instance, puts it in the ApplicationContext. That's it.

### The variants — all do the same thing, just signal intent

```java
@Component    // generic — use when none of the below fit
@Service      // signals: I am a business/service layer class
@Repository   // signals: I am a data access class + gets exception translation
@Controller   // signals: I am a web layer class
@RestController // @Controller + @ResponseBody combined
```

All four register the class as a bean. The difference is semantic — they tell
other developers (and Spring) what role the class plays.

### @Repository gets something extra — exception translation
Spring wraps database exceptions (like Hibernate-specific ones) into Spring's
own exception hierarchy. This means your service layer doesn't need to know
about JPA-specific exceptions. It just catches Spring's DataAccessException.

### MPPS examples
- `@Service TransactionServiceImpl` — business logic layer
- `@Service IdempotencyServiceImpl` — business logic layer
- `@RestController TransactionController` — web layer
- Spring Data repositories are auto-registered by Spring Data (no annotation needed)

### The rule
Use @Component (or its variants) when you WROTE the class and can put
the annotation directly on it.

---

## Way 2 — @Bean inside @Configuration

### The problem @Component can't solve
What if you need to register a class from a third-party library?
You didn't write it. You can't edit its source code. You can't put @Component on it.

Example: `BCryptPasswordEncoder` is from Spring Security.
`RestTemplate` is from Spring Web. `ObjectMapper` is from Jackson.
You need Spring to manage these — but you can't touch their source code.

### The solution
```java
@Configuration                         // tells Spring: this class holds bean definitions
public class ApplicationConfig {

    @Bean                              // tells Spring: run this method, store what it returns
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

This is exactly what your MPPS ApplicationConfig.java does.

Spring calls `passwordEncoder()`, gets back a `BCryptPasswordEncoder`, stores it
as a bean. Now anywhere in MPPS that needs BCryptPasswordEncoder, Spring injects
it automatically — even though you never put @Component on BCryptPasswordEncoder.

### @Configuration vs @Bean — they work together
- `@Configuration` goes on the CLASS — "this class contains bean definitions"
- `@Bean` goes on the METHOD inside that class — "run this, store the result"
- You cannot have @Bean without @Configuration (well, you can, but it loses
  important proxy behaviour — always use them together)

### The rule
Use @Bean when you need to register something you DON'T own, or when you need
full control over how an object is constructed (custom parameters, conditions etc).

---

## The interview question that tripped you

Interviewer: "So are you saying @Component classes can't be manually configured?"

The WRONG answer (what you said): "Components are automatically managed and
beans are manually managed" — this implies @Component can't be configured.

The CORRECT answer: "@Component classes absolutely CAN be manually configured.
The difference between @Component and @Bean is about WHO OWNS THE CLASS,
not about manual vs automatic configuration."

### How to manually configure @Component classes

```java
// @Qualifier — when multiple beans of same type exist, specify which to inject
@Service
@Qualifier("primaryTransactionService")
public class TransactionServiceImpl implements TransactionService { ... }

// @Primary — mark as the default bean when multiple exist
@Service
@Primary
public class TransactionServiceImpl implements TransactionService { ... }

// @Scope — change the scope
@Service
@Scope("prototype")
public class ReportGenerator { ... }

// @Lazy — don't create until first needed
@Service
@Lazy
public class HeavyReportingService { ... }
```

---

## Side by side comparison

| | @Component | @Bean |
|---|---|---|
| Goes on | A CLASS | A METHOD |
| Who owns the class | You | Third party library |
| How Spring finds it | Classpath scanning | Called explicitly by Spring |
| Lives inside | Anywhere in scanned package | A @Configuration class |
| Can be manually configured | YES — @Qualifier, @Primary etc | YES — constructor args, conditions |
| MPPS example | TransactionServiceImpl | BCryptPasswordEncoder |

---

## Common mistake to avoid
Using @Component on a class when you actually need custom construction logic.
If your bean needs specific constructor arguments or conditional setup — use @Bean.
@Component is for simple auto-detected classes. @Bean is for when you need control.

---

## Interview answer (say this verbatim)
"There are two ways to register a bean in Spring. @Component goes on a class
you own and Spring auto-detects it via classpath scanning. @Bean goes on a method
inside a @Configuration class and is used for third-party classes you can't annotate
directly. Both can be manually configured — the difference is about who owns the
class, not about manual vs automatic."

## Quick summary
- @Component = class you own, found via classpath scan
- @Bean = method inside @Configuration, for classes you don't own
- @Configuration = marks a class as containing @Bean definitions
- @Component classes CAN be manually configured — @Qualifier, @Primary, @Scope, @Lazy
- The distinction is WHO OWNS THE CLASS — not manual vs automatic

## Code reference
See: MPPS — ApplicationConfig.java (@Bean examples)
See: MPPS — TransactionServiceImpl.java (@Service example)
