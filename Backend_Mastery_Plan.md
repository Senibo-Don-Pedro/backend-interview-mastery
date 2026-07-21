# Your Backend Mastery & AWS SAA Roadmap

## The Core Philosophy
1. **Build First, Theory Second:** You already know how to build things. Do not waste time watching 10-hour tutorials on tools you already use (like Spring Boot).
2. **One Primary Focus:** Java Fundamentals & Data Structures (DSA).
3. **One Secondary Focus:** AWS Solutions Architect Associate (SAA) Certification.

---

## Phase 1: Deepen Java & DSA (Primary Focus)
*You use Java every day. Strengthening this gives the highest immediate return on investment.*

### Actionable Steps
1. **Java Core:** Complete the **MOOC.fi Java Programming** course. It forces you to write code and passes it against tests.
2. **DSA Theory:** Watch **William Fiset's Data Structures in Java** on YouTube. Understand *why* HashMaps and Arrays work under the hood.
3. **DSA Practice:** Focus on **Exercism (Java Track)** for idiomatic Java feedback, and **NeetCode 150** for interview patterns.

---

## Phase 2: Address the "Vague" Theory (Spring Boot, Security)
*You don't need tutorials for this. You need reference reading.*

1. **Spring Boot Theory:** Instead of tutorials, read articles on **Baeldung.com**. Whenever you use an annotation like `@Autowired` or `@Transactional`, read the Baeldung article to understand the "magic" behind it (e.g., Dependency Injection, Proxy objects).
2. **Security Theory (Hashing & Encryption):** You are already on the right track! Just continue updating your `backend-interview-mastery` repo. When implementing a feature (like MFA or passwords), write a 1-page note on the difference between Hashing (one-way, e.g., bcrypt) vs Encryption (two-way, e.g., AES).

---

## Phase 3: AWS SAA Certification (Secondary Focus)
*Goal: Utilize your 600k NGN exam budget before the end of the year.*

### Actionable Steps
1. **Downtime Learning:** Watch your Udemy AWS SAA course (Stephane Maarek) during commutes, lunch breaks, or when you are too tired to code.
2. **Mental Mapping:** Constantly ask yourself how your current Spring Boot app maps to AWS (e.g., "Where does this `ConcurrentHashMap` go if we have 5 servers? ElastiCache.").
3. **Labs:** Do the hands-on labs in the course to solidify the concepts.

---

## Daily / Weekly Routine Example
- **Work Hours:** Build features (like the MFA session fix). Apply what you learn.
- **Evening (1-2 Hours):** Active Coding (MOOC.fi or NeetCode).
- **Downtime (30 mins):** Passive Learning (AWS videos or reading Baeldung).
- **Weekend:** Update your `backend-interview-mastery` GitHub repo with what you learned that week.
