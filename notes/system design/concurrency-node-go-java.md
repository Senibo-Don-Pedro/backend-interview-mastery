# How Node.js, Go & Java Handle Concurrency

## The root problem: blocking I/O

When a Java thread hits a DB call, it just *sits there waiting* for the response. It's not doing any work — it's just blocked. This is the real enemy, not threads themselves.

So the question becomes: **can we free the thread while it waits?**

---

## How Node.js solves it — the Event Loop

Node is single-threaded. Literally **one** thread. Yet it handles thousands of requests. How?

When Node hits a DB call, instead of waiting, it says:
> *"Hey OS, call me back when the data is ready"* — and immediately moves on to the next request.

When the DB responds, Node picks up where it left off. This is the **event loop** — one worker juggling thousands of tasks by never sitting idle.

```
Request 1: DB call → not waiting, moving on...
Request 2: DB call → not waiting, moving on...
Request 3: computation → done!
[DB responds for Request 1] → resume Request 1
```

**The catch:** if you do anything CPU-heavy (e.g. image processing, complex calculations) on that one thread, *everything* grinds to a halt. Node is great at I/O, terrible at CPU work.

---

## How Go solves it — Goroutines

Go takes a different approach. Instead of OS threads (expensive, ~1MB each), Go has **goroutines** — ultralight virtual threads (~2KB each).

You can spin up **100,000 goroutines** and Go's runtime schedules them across your actual CPU cores automatically. When a goroutine blocks on I/O, the Go scheduler parks it and runs another one — no OS involvement needed.

```go
// This spawns a goroutine — costs almost nothing
go handleRequest(req)
```

It feels like threading but without the cost. This is why Go is exceptional for highly concurrent network services.

---

## How Java solves it — Virtual Threads (Java 21)

Java historically had the same problem as Go *used to* have — one OS thread per request, expensive and limited. Java 21 introduced **Virtual Threads** (Project Loom), directly inspired by Go's goroutines.

```java
// Old way — one expensive OS thread per request
Executors.newFixedThreadPool(200);

// Java 21 — millions of cheap virtual threads
Executors.newVirtualThreadPerTaskExecutor();
```

Virtual threads are managed by the JVM, not the OS. When one blocks on a DB call, the JVM parks it and runs another — exactly like Go's scheduler. You can now have **millions** of virtual threads in Java without running out of memory.

Spring Boot 3.2+ enables this with one line in `application.yml`:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

---

## Side by side

| | Node.js | Go | Java (modern) |
|---|---|---|---|
| Approach | Single thread + event loop | Goroutines (virtual threads) | Virtual Threads (Java 21+) |
| Concurrency model | Async/non-blocking callbacks | Lightweight goroutines | JVM-managed virtual threads |
| Blocking I/O cost | Handled by event loop | Goroutine parked, another runs | Virtual thread parked, another runs |
| CPU-heavy work | ❌ Blocks everything | ✅ Spread across cores | ✅ Spread across cores |
| Thousands of concurrent requests? | ✅ Yes | ✅ Yes | ✅ Yes (Java 21+) |

---

## So why did Java struggle before?

Because before Java 21, every request = one OS thread. OS threads cost ~1MB of memory and are slow to create. 10,000 concurrent requests = 10GB of RAM just for threads. That's why connection pools were so critical — they were a workaround for a deeper problem.

Virtual threads largely solve this. But connection pools are still relevant — your *database* still has connection limits regardless of how many virtual threads your app has.
