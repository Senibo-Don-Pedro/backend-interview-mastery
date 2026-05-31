# 07 — IoC Container and Dependency Injection

## What is it?
IoC = stop creating your dependencies yourself. Let Spring create them and hand them to you.
DI = the mechanism Spring uses to hand dependencies to your classes (via constructor).

## The ApplicationContext
Spring's object factory — a warehouse of all managed objects (beans).
Created at startup. Every @Component, @Service, @Repository, @Controller gets registered here.

```
ApplicationContext
├── TransactionServiceImpl  (one instance)
├── WalletRepository        (one instance)
├── IdempotencyServiceImpl  (one instance)
└── ... every Spring-managed object
```

## How DI works in your MPPS code
```java
@Service
@RequiredArgsConstructor          // Lombok generates the constructor
public class TransactionServiceImpl {
    private final TransactionRepository transactionRepository;  // injected by Spring
    private final WalletRepository walletRepository;            // injected by Spring
}
```
Spring sees the constructor, finds each type in the ApplicationContext, passes them in.
You never write `new TransactionServiceImpl(...)` — Spring does it at startup.

## How Spring knows what to manage
| Annotation | What it signals |
|---|---|
| `@Component` | Generic Spring-managed class |
| `@Service` | Business/service layer |
| `@Repository` | Data access layer (+ exception translation) |
| `@Controller` | Web layer |

All four work the same way — they register the class as a bean.

## Constructor vs field injection
```java
// Field injection — BAD
@Autowired
private WalletRepository walletRepository; // hidden dependency, can't be final

// Constructor injection — GOOD (what MPPS uses via @RequiredArgsConstructor)
private final WalletRepository walletRepository; // immutable, explicit, testable
```
Constructor injection is preferred: dependencies are explicit, fields can be final,
and you can pass mocks in tests without Spring at all.

## Interview answer
"The ApplicationContext is Spring's object factory. At startup it scans for annotated
classes, creates singleton instances, registers them as beans, and uses constructor
injection to wire dependencies automatically. IoC inverts who controls object creation
— from your code to the framework."

## Quick summary
- IoC = Spring controls object creation, not you
- DI = Spring injects dependencies via constructor at startup
- ApplicationContext = the warehouse holding all beans
- Always use constructor injection (final fields + @RequiredArgsConstructor)

## Code reference
See: MPPS — TransactionServiceImpl.java, TransactionWorker.java
