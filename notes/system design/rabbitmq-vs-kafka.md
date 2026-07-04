# RabbitMQ vs Kafka

## Source material

The core difference is that Message Queueing is a point-to-point communication mechanism designed to distribute commands or workloads to exactly one consumer, whereas Event-Driven Architecture (EDA) is an entire system design pattern where multiple independent services react to a broadcasted notification of something that has already happened. In short: a queue tells a specific destination what to do, while an event announces to anyone listening what just happened.

| Feature | Message Queuing (Queue-Based) | Event-Driven Architecture (EDA) |
|---|---|---|
| Intent / Focus | Executing a specific command or task. | Broadcasting a notable state change. |
| Message Payload | Commands ("ProcessPayment", "ResizeImage"). | Facts ("PaymentProcessed", "ImageUploaded"). |
| Communication Model | Point-to-Point (one-to-one). | Publish-Subscribe / Streaming (one-to-many). |
| Consumer Lifecycle | Deletes data immediately after it is read. | Can retain history for replayability (e.g., event log). |
| Coupling Level | Semantically coupled (Sender knows what destination should do). | Highly decoupled (Publisher has zero awareness of consumers). |
| Common Tools | RabbitMQ, AWS SQS. | Apache Kafka, AWS SNS, Apache Flink. |

---

## My notes

**What is it?** Two ways to pass messages between services — but with different intents. One distributes tasks, the other broadcasts events.

---

## The one-liner

| | |
|---|---|
| **RabbitMQ** | *Tells* someone what to do → "SendEmail" |
| **Kafka** | *Announces* what just happened → "EmailSent" |

---

## Core concepts

**Message broker** — A middleman that receives messages from senders and routes them to receivers. Decouples who sends from who receives.

**Event streaming** — A continuous log of things that happened. History is retained, multiple services can read the same event independently.

---

## Key differences

| Feature | RabbitMQ | Kafka |
|---|---|---|
| Model | Point-to-point (1 → 1) | Pub-sub (1 → many) |
| Payload | Commands ("ProcessPayment") | Facts ("PaymentProcessed") |
| After reading | Message deleted | Message retained (replayable) |
| Consumers | Exactly one picks it up | Any number can listen |
| Coupling | Sender knows what receiver should do | Publisher has no idea who listens |

---

## When to use which

**Use RabbitMQ when:**
- One job must be handled by exactly one worker
- Task should only run once (e.g. send one email, resize one image)
- You want simple, low-latency task distribution

**Use Kafka when:**
- Multiple services need to react to the same event (e.g. analytics, notifications, audit log — all triggered by one payment)
- You need to replay past events (audit logs, debugging)
- You're building a large decoupled system

---

## Real-world analogy

- **RabbitMQ** = a task list. One worker picks up the task, it disappears.
- **Kafka** = a newspaper. Many people read the same paper. Old editions stay in the archive.

---

## Common tools

- Message queues: RabbitMQ, AWS SQS
- Event streaming: Apache Kafka, AWS SNS, Apache Flink
