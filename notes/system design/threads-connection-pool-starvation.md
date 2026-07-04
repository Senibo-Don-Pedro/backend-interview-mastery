# Threads, Connection Pooling & Thread Starvation

## What is a thread?

A thread is a worker. When a request comes into your Spring Boot app (e.g. a branch submitting a cheque), a worker (thread) picks it up and starts processing it. Your server has a limited number of workers — by default, Tomcat gives you around 200 threads.

---

## What is a connection pool?

Your app can't call the database directly without a *connection* — think of it like a phone line to the DB. Opening a new phone line for every single request is slow and expensive. So instead, you keep a small set of lines permanently open and ready — that's the **connection pool**.

**HikariCP** is Spring Boot's default connection pool manager. It maintains a pool of reusable DB connections so threads don't have to create one from scratch on every request.

> Default pool size in HikariCP: **10 connections**

---

## What is thread starvation?

Imagine all 10 connections in the pool are in use, each held by a slow-running query. A new request comes in, grabs a thread, asks the pool for a connection — and waits. And waits. If it waits too long, you get:

```
Connection is not available, request timed out after 30000ms
```

That's **thread starvation** — your threads are alive but stuck doing nothing, just waiting for a free connection from the pool.

---

## Can you increase the pool size?

Yes, easily — in your `application.yml`:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20       # default is 10
      connection-timeout: 30000   # how long to wait before throwing error (ms)
```

But more connections isn't always the answer. Your database server also has a limit on how many connections it can handle simultaneously. The real fix is usually **faster queries** so connections are released sooner — indexes, fixing N+1 queries, caching, etc.

---

## Quick summary

| Concept | Plain English |
|---|---|
| Thread | A worker that handles one incoming request |
| Connection | A phone line to the database |
| Connection pool | A pre-opened set of connections shared across threads |
| HikariCP | Spring Boot's default connection pool manager |
| Thread starvation | Threads stuck waiting because the pool has no free connections |

---

## How it relates to diagnosing a slow endpoint

When an endpoint is slow under load, connection pool exhaustion is one of the first things to check — alongside slow queries, N+1 problems, and missing indexes. The symptoms look similar (slow response times, timeouts) but the fix is different:

- **Slow query** → add indexes, fix N+1, optimise the query
- **Pool exhaustion** → increase pool size, or fix slow queries so connections are freed faster
- **Thread starvation** → reduce blocking I/O, offload background work to a queue (e.g. Kafka)
