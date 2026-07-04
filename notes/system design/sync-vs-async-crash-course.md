# Sync vs Async — A Crash Course

## 1. What is a program?

Your code runs on a **CPU** (the processor). The CPU executes instructions one at a time, extremely fast. A running program is called a **process** — it has its own memory, its own resources, its own space.

---

## 2. What is a thread?

A **thread** is a sequence of instructions running inside a process. Think of a process as a restaurant, and threads as the waiters inside it.

One waiter = one thread. They can only do one thing at a time — take an order, go to the kitchen, serve food.

A process can have **multiple threads** — multiple waiters working simultaneously inside the same restaurant, sharing the same kitchen (memory).

---

## 3. What is I/O?

**I/O** means Input/Output — any operation where your program talks to something *outside the CPU*:

- Reading from a database
- Making an HTTP call to another service
- Reading a file from disk
- Waiting for a network response

The key thing about I/O: **the CPU isn't doing any work during it**. It's just waiting for a response from somewhere else. Like a waiter who placed an order and is now just standing at the kitchen window doing nothing.

---

## 4. Blocking vs Non-blocking

**Blocking I/O** — the thread stops and waits for the I/O to finish before doing anything else.

```
Thread: "Hey DB, give me this data."
Thread: *stares at wall*
Thread: *still staring*
DB: "Here's your data!"
Thread: "Oh great, continuing now."
```

The thread is alive but useless while waiting. This is the default in Java.

**Non-blocking I/O** — the thread fires the request, registers a callback ("call me when done"), and immediately moves on to other work.

```
Thread: "Hey DB, give me this data. Call me when ready."
Thread: *immediately handles another request*
DB: "Here's your data!"
Thread: "Got it, resuming that first request now."
```

Same thread, doing twice the work. This is how Node.js works.

---

## 5. Synchronous vs Asynchronous

These words map almost directly to blocking vs non-blocking, but at the *code* level.

**Synchronous** — code executes line by line. The next line doesn't run until the current one finishes.

```java
// Synchronous — waits for DB before moving to the next line
User user = userRepository.findById(id); // blocks here
sendEmail(user); // only runs after DB responds
```

**Asynchronous** — you kick off an operation and provide something to run when it completes. The next line runs immediately without waiting.

```javascript
// Asynchronous — doesn't wait
db.findUser(id).then(user => sendEmail(user)); // registered callback
console.log("This runs immediately, before DB responds");
```

Synchronous code is easier to read and reason about. Asynchronous code is harder to follow but far more efficient under load.

---

## 6. CPU-heavy work vs I/O-heavy work

This distinction matters a lot for choosing the right tool.

**I/O-heavy (I/O-bound)** — your program spends most of its time *waiting* for external responses. DB calls, API calls, file reads. The CPU is mostly idle.

> Most web backends are I/O-bound. Your Spring Boot app isn't crunching numbers — it's mostly waiting on the database.

**CPU-heavy (CPU-bound)** — your program spends most of its time *actually computing*. Image processing, video encoding, machine learning inference, cryptography, sorting massive datasets.

> The CPU is maxed out. Adding more threads doesn't help much — you need more CPU cores.

This is why Node.js is fine for APIs but bad for image processing — one blocked CPU-heavy task freezes the entire event loop.

---

## 7. Multithreading

**Multithreading** = running multiple threads simultaneously inside one process.

Back to the restaurant analogy — instead of one waiter, you hire five. Now five customers can be served at once. The kitchen (memory) is shared, so the waiters need to coordinate to avoid chaos (this is called a **race condition** — two threads modifying the same data at the same time).

Java's traditional model is multithreaded — Tomcat gives you 200 OS threads and assigns one per request.

**The cost:** OS threads are expensive (~1MB RAM each). 10,000 threads = 10GB RAM. That's the ceiling.

---

## 8. Virtual Threads

**Virtual threads** (also called green threads or goroutines in Go) are threads managed by the *language runtime*, not the OS.

They're tiny — Go's goroutines start at ~2KB, Java virtual threads are similarly lightweight. The runtime can park a virtual thread the moment it blocks on I/O and immediately run another — all without involving the OS at all.

This is why you can have **millions** of virtual threads but only **thousands** of OS threads.

| | OS thread | Virtual thread |
|---|---|---|
| RAM cost | ~1MB | ~2KB |
| Managed by | Operating system | JVM / Go runtime |
| Creation speed | Slow | Almost free |
| Blocks on I/O? | Yes — wastes the thread | Parked, another runs immediately |

Virtual threads are the reason Go and modern Java can handle massive concurrency without the async/callback complexity of Node.

---

## 9. When to use what

| Situation | Best approach |
|---|---|
| Standard REST API, mostly DB calls | Synchronous + virtual threads (Java 21) or Go |
| Extreme I/O concurrency, simple logic | Node.js async / Go goroutines |
| CPU-heavy work (encoding, ML) | Multithreading across cores, or dedicated workers |
| Background jobs, decoupled processing | Message queue (Kafka/RabbitMQ) + separate workers |
| Mixed I/O + CPU | Go or Java — both handle multi-core well |

---

## The mental model to remember

> A thread waiting on I/O is wasted capacity. Every concurrency solution — async callbacks, goroutines, virtual threads, event loops — is just a different answer to the same question: **"What should the CPU do while it waits?"**
