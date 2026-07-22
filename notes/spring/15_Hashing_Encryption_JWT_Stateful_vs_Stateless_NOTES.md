# Hashing, Encryption, JWT, and Stateful vs Stateless

## The Problems This Solves

**Problem 1 — storing passwords.** If MPPS stores passwords as plain text, one leaked backup or careless log statement exposes every user's actual password. You need to verify "is this the right password?" without ever being able to recover the original — not even you, the developer, should be able to see it.

**Problem 2 — proving identity across stateless requests.** Once logged in, every subsequent request needs to say "I am user X" — but MPPS has no server-side session. You need something the client holds and sends with every request, that the server can quickly verify wasn't forged, without a database lookup on every call.

Different problems, different tools: **hashing** solves problem 1, **JWT** (encoding + cryptographic signing) solves problem 2.

---

## Part 1 — Hashing vs Encryption

```java
// ENCRYPTION — reversible. A key gets you back the original.
String encrypted = encrypt("mySecretPassword", key);
String original  = decrypt(encrypted, key);  // ← "mySecretPassword", recovered

// HASHING — one-way. There is no decrypt() function. Ever.
String hashed = hash("mySecretPassword");
// there is no unhash(hashed) that returns "mySecretPassword"
```

**Encryption**: for data you need back later (e.g., a bank account number you must read to process a payment). Reversible by design, given the right key.

**Hashing**: for data you never need to see again — you only ever check "does this match?" MPPS never needs to know a user's actual password, only whether they typed the right one.

### Why plain SHA-256 is unsafe for passwords

```java
// DON'T DO THIS
String hash = MessageDigest.getInstance("SHA-256").digest("password123".getBytes());
```

SHA-256 is deliberately **fast** — built for file integrity checks, not passwords. That speed is the vulnerability: an attacker with a leaked hash database can try billions of guesses per second on cheap hardware (`hash("123456")`, `hash("password")`...) and crack most of them almost instantly. This is a **brute-force attack**.

**Second problem — identical inputs, identical outputs.** Two users with the password `"password123"` would get the *exact same hash*. Cracking one instantly reveals every account sharing that password. This is solved by **salting** — but modern libraries like BCrypt handle salting automatically.

---

## Part 2 — BCrypt: How It Actually Works Under the Hood

```java
@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();  // default work factor: 10
    }
}
```

```java
@Service
public class UserService {
    private final PasswordEncoder passwordEncoder;

    public void registerUser(String rawPassword) {
        String hashed = passwordEncoder.encode(rawPassword);
        // store `hashed` — NEVER the raw password
    }

    public boolean checkPassword(String rawPassword, String storedHash) {
        return passwordEncoder.matches(rawPassword, storedHash);
    }
}
```

### Anatomy of a BCrypt hash string

BCrypt does **not** store the salt separately anywhere — it embeds the salt directly inside the returned hash string itself. There's no separate "salt column" needed in your database.

```
$2a$10$N9qo8uLOickgx2ZMRZoMy.Mrq4kNjE3M4WjTiDy4Tqu9NN9m8v1S
└┬┘└┬┘└──────────┬───────────┘└─────────────┬─────────────┘
 │  │            │                           │
 │  │            │                           └── the actual hash output (31 chars)
 │  │            └── the SALT (22 chars) — randomly generated fresh at encode() time
 │  └── the WORK FACTOR / cost (e.g. "10" → 2^10 = 1,024 rounds)
 └── algorithm version identifier ("2a" = a specific BCrypt variant)
```

**What happens when you call `encode()`:**
1. BCrypt generates a fresh random salt (that 22-character segment).
2. Runs the hashing algorithm using that salt and the given work factor.
3. Concatenates version + work factor + salt + resulting hash into **one single string**.
4. Returns that entire string — store it in the DB exactly as-is.

```java
passwordEncoder.encode("password123");  // $2a$10$abc123...xyz  (random salt A)
passwordEncoder.encode("password123");  // $2a$10$def456...uvw  (random salt B — different!)
```

Both are valid hashes of the same password — they just look completely different because each used its own random salt.

### How `matches()` verifies without ever "decrypting" anything

```java
passwordEncoder.matches(rawPassword, storedHash);
```

Step by step:
1. Take `storedHash`: `$2a$10$N9qo8uLOickgx2ZMRZoMy.Mrq4kNjE3M4WjTiDy4Tqu9NN9m8v1S`
2. Extract the salt segment from it: `N9qo8uLOickgx2ZMRZoMy` (BCrypt knows exactly which characters are the salt, since the format is fixed-width and self-describing).
3. Hash the freshly-typed `rawPassword` using **that same extracted salt**.
4. Compare the newly computed hash against the hash portion of `storedHash`.
5. Identical → correct password. Different → wrong password.

**There is no decryption step anywhere.** This is pure re-computation: BCrypt reads the salt back out of the stored string (it's sitting there in plain sight, not hidden), reuses it to hash the newly entered password, and checks whether the two outputs match. This is exactly why hashing is one-way — you never reverse anything; you recompute the forward direction with the same ingredients and compare results.

### What the work factor (`10`, sometimes written `BCryptPasswordEncoder(10)`) actually means

```java
new BCryptPasswordEncoder(10);  // default
new BCryptPasswordEncoder(12);  // stronger, slower
```

The work factor controls internal hashing rounds, and it's **exponential**:

```
work factor 10 → 2^10 = 1,024 rounds
work factor 11 → 2^11 = 2,048 rounds   (2x slower than 10)
work factor 12 → 2^12 = 4,096 rounds   (4x slower than 10)
```

Each +1 doubles the computation time. Work factor 10 (~50-100ms per hash on typical hardware) is imperceptible for a real user logging in once, but makes brute-forcing billions of leaked hashes computationally punishing. As hardware gets faster, recommended defaults creep upward over the years — which is exactly why BCrypt bakes the work factor into the hash string itself: it lets you re-hash old passwords at a higher cost as users naturally log in, without invalidating passwords hashed under an older, lower cost.

---

## Part 3 — JWT: Proving Identity Across Stateless Requests

A JWT is three Base64-encoded segments separated by dots:

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzZW5pYm8iLCJyb2xlIjoiVVNFUiJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
└──────── HEADER ────────┘ └──────────── PAYLOAD ────────────┘ └──────────── SIGNATURE ────────────┘
```

Decoded:

```json
// HEADER — algorithm used to sign
{ "alg": "HS256", "typ": "JWT" }

// PAYLOAD — actual claims about the user
{ "sub": "senibo", "role": "USER", "iat": 1721520000, "exp": 1721523600 }

// SIGNATURE — cryptographic proof header+payload weren't tampered with
```

**Critical gotcha:** header and payload are only **Base64-encoded, not encrypted**. Anyone can decode them instantly (paste into jwt.io). **Never put secrets in a JWT payload.** The payload is effectively public; only the signature is protected.

### How the signature works

```java
signature = HMACSHA256(
    base64UrlEncode(header) + "." + base64UrlEncode(payload),
    secretKey
)
```

The signature is a hash of header+payload computed with a **secret key only the server knows**. If anyone edits the payload (e.g. changing `"role": "USER"` to `"role": "ADMIN"`), the signature no longer matches, because recomputing `HMACSHA256(newPayload, secretKey)` produces a completely different signature than the one attached to the original token. Tampering is instantly detectable without a database lookup.

```java
@Service
public class JwtService {

    private final SecretKey secretKey = Keys.hmacShaKeyFor(
        "your-256-bit-secret-key-stored-in-env-vars".getBytes()
    );

    public String generateToken(String username, String role) {
        return Jwts.builder()
            .setSubject(username)
            .claim("role", role)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600_000)) // 1 hour
            .signWith(secretKey)
            .compact();
    }

    public boolean isValid(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;  // tampered, expired, or malformed
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(secretKey).build()
            .parseClaimsJws(token).getBody();

        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        return new UsernamePasswordAuthenticationToken(
            username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}
```

This connects directly to `JwtAuthenticationFilter` from the security filter chain topic — `isValid()` and `getAuthentication()` are exactly what that filter calls to decide what to place into the `SecurityContext`.

---

## Part 4 — Stateful vs Stateless

**State** = information the server remembers about you between requests.

### Stateful (traditional sessions)

```
Request 1: POST /login  { username, password }
   → Server verifies credentials
   → Server creates a session IN SERVER MEMORY (or shared store like Redis):
       sessionId: "abc123" → { userId: 42, role: "USER", loggedInAt: ... }
   → Server sends back: Set-Cookie: JSESSIONID=abc123

Request 2: GET /wallet/balance
   Cookie: JSESSIONID=abc123
   → Server looks up "abc123" in its session store → finds { userId: 42, ... }
   → Now it knows who you are
```

The server **holds onto information about you** across requests — that's "stateful." The session lives server-side; the client just holds a pointer (the session ID).

**The scaling problem:** if MPPS runs on 3 instances behind a load balancer, and login hits Server A (session created in Server A's memory), but the next request routes to Server B — Server B has never heard of session "abc123." Fixing this requires a shared session store (Redis), adding infrastructure and a network call on every request.

### Stateless (JWT)

```
Request 1: POST /login  { username, password }
   → Server verifies credentials
   → Server generates a JWT containing everything needed: { userId: 42, role: "USER" }, signs it
   → Server returns it — stores NOTHING about this login anywhere

Request 2: GET /wallet/balance
   Authorization: Bearer eyJhbGc...
   → Server verifies the SIGNATURE (pure math, no lookup)
   → If valid, server trusts everything inside the token
   → Now it knows who you are — without having stored anything
```

The server remembers **nothing** between requests. Every request carries its own complete, self-contained proof of identity. Any of MPPS's server instances can verify any token independently — no shared session store, no database hit, no coordination needed. This is the entire appeal of statelessness for horizontally-scaled APIs.

**The tradeoff:** with sessions, you can instantly revoke access (delete the session — user is logged out immediately). With JWT, once issued, a token can't be "unissued" — it stays valid until it naturally expires, unless you build extra infrastructure like a token blocklist (which partially reintroduces state).

---

## Interview Gotchas — Summary

- **"Is hashing the same as encryption?"** → No. Encryption is reversible with a key; hashing is one-way, with no decrypt function. Passwords should be hashed, never encrypted.
- **"Why not just use SHA-256 for passwords?"** → It's deliberately fast, which makes brute-forcing billions of guesses cheap for an attacker. BCrypt is deliberately slow via its work factor, and auto-salts every hash.
- **"If BCrypt's salt is random every time, how does it verify later?"** → The salt isn't stored separately — it's embedded directly inside the returned hash string. `matches()` extracts it back out and re-hashes the input password with that same salt, then compares outputs. No decryption occurs.
- **"What does the number in `BCryptPasswordEncoder(10)` mean?"** → The work factor / cost. It's exponential: each +1 doubles the number of hashing rounds (2^10 = 1,024 rounds → 2^12 = 4,096 rounds), trading speed for brute-force resistance.
- **"Is a JWT encrypted?"** → No — header and payload are only Base64-**encoded**, fully readable by anyone. Only the signature is cryptographically protected. Never put secrets in the payload.
- **"What actually stops someone from editing a JWT payload?"** → The signature is a hash of header+payload made with a server-only secret key. Any edit to the payload makes the signature mismatch on verification, and the token is rejected.
- **"Why is JWT good for stateless APIs?"** → All identity information travels inside the token itself. No server-side lookup, no shared session store — any server instance can verify any token independently, which is ideal for horizontal scaling.
- **"What's the tradeoff of going stateless with JWT?"** → You lose the ability to instantly revoke a single token; it stays valid until expiry unless you add extra infrastructure (a blocklist), which reintroduces some state.

---

## Quick Summary

- **Hashing** is one-way (verify without ever recovering the original); **encryption** is reversible with a key. Passwords must be hashed, never encrypted.
- **BCrypt** auto-salts every hash and uses a deliberately slow, exponential work factor to resist brute-forcing.
- The salt is **embedded inside** the BCrypt hash string itself (fixed-width, self-describing format) — never stored separately. `matches()` re-extracts it and recomputes, it never decrypts.
- Work factor (e.g. `10`) is exponential: each increment doubles hashing time (2^n rounds).
- **JWT** = header.payload.signature, Base64-encoded (not encrypted) header/payload + a cryptographic signature that detects tampering.
- **Stateful** systems (sessions) store identity server-side; **stateless** systems (JWT) embed identity inside the token itself, trading easy revocation for zero-lookup horizontal scalability.

## Code Reference (MPPS)

- `SecurityConfig.passwordEncoder()` — the `BCryptPasswordEncoder` bean
- `UserService.registerUser()` / `checkPassword()` — where hashing and verification happen
- `JwtService` — token generation (`generateToken`), validation (`isValid`), and identity extraction (`getAuthentication`)
- `JwtAuthenticationFilter` (from the filter chain topic) — the consumer of `JwtService`, feeding results into `SecurityContextHolder`
- `.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))` — the config line that explicitly opts MPPS out of session-based state

---

## Addendum — Real-World Case Study: Multi-Step Auth (Login → 2FA) at Scale

### The scenario

A common pattern in banking-adjacent systems: step 1 verifies a password (sometimes against an external identity provider, e.g. an Active Directory, rather than a locally stored hash), and on success returns an intermediate credential. Step 2 (OTP/2FA) requires that intermediate credential instead of the raw username again — this prevents someone from skipping straight to the OTP endpoint without having proven their password first.

```
Step 1: POST /login { username, password }
   → verify password (e.g. against an external AD, not a local hash)
   → on success, issue some intermediate value X
   → return X to the client

Step 2: POST /verify-otp { X, otpCode }
   → resolve X back to a username
   → verify the OTP for that username
   → issue the real JWT
```

This overall shape is a legitimate, common pattern — often called a **pre-authentication token** or **intermediate token**. Verifying a password against an external identity provider instead of a local hash is also a sound pattern — a stateless identity check with zero local password storage.

### Where this breaks at scale

If the intermediate value `X` is a **session ID stored in a single server's memory** (an in-memory map, or a default `HttpSession`), it only works as long as every request from the same user happens to land on the same server:

```
Step 1 (login)     → hits Server A → session "sess_abc123" created in Server A's memory
Step 2 (2FA verify) → hits Server B (load balancer routed here) → Server B has never heard of "sess_abc123"
→ 2FA fails, even though the user did everything correctly
```

With a single server, this never surfaces, because every request happens to land in the same place. The moment you scale horizontally, it becomes an intermittent, hard-to-reproduce bug that depends entirely on which server instance the load balancer happens to route each request to. This is the exact stateful-scaling problem described in Part 4 above, showing up in a real multi-step login flow.

### Fix 1 — Shared store (Redis), keeping the session-id shape

```java
@Service
public class PreAuthSessionService {

    private final RedisTemplate<String, String> redisTemplate;

    public String createPreAuthSession(String username) {
        String sessionId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
            "preauth:" + sessionId,
            username,
            Duration.ofMinutes(5)   // short TTL — should expire fast
        );
        return sessionId;
    }

    public Optional<String> resolveUsername(String sessionId) {
        String username = redisTemplate.opsForValue().get("preauth:" + sessionId);
        return Optional.ofNullable(username);
    }
}
```

Any server instance can now resolve the session ID, because Redis is shared infrastructure rather than one server's local memory. This preserves the original flow (session id → username lookup) while fixing the scaling problem.

### Fix 2 — Make it stateless: a short-lived signed token instead of a session ID

```java
public String createPreAuthToken(String username) {
    return Jwts.builder()
        .setSubject(username)
        .claim("purpose", "PRE_AUTH")        // scope it — see gotcha below
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + 5 * 60_000)) // 5 min
        .signWith(secretKey)
        .compact();
}
```

The 2FA endpoint verifies the signature (pure math, no lookup, no shared store required) and reads the username directly out of the claims — the same verification mechanism as a normal login JWT, just with a shorter expiry and a narrower purpose. This avoids standing up Redis purely for this, and is consistent with an otherwise fully stateless system.

### Gotcha — scope the token's purpose explicitly

Don't reuse the same JWT structure used for full login sessions. Add an explicit `purpose`/`scope` claim and check it on the receiving endpoint:

```java
if (!"PRE_AUTH".equals(claims.get("purpose", String.class))) {
    throw new InvalidTokenException("Wrong token type for this endpoint");
}
```

Without this check, a fully-authenticated normal login JWT could be replayed against the 2FA endpoint, or a pre-auth token could mistakenly be accepted somewhere it shouldn't be. Same signing mechanism, but the token's *purpose* must be explicit and explicitly checked — an easy-to-miss vulnerability in multi-step auth flows.

### Gotcha — one-time use

Whichever option is used, the pre-auth session/token should only be usable **once**. If it's valid and reusable for its whole 5-minute window, an intercepted value lets an attacker attempt the OTP step repeatedly. With Redis, delete the key immediately after successful use. With a stateless JWT, there's no built-in way to mark a token "already used" — you'd need to track used-token identifiers somewhere, which reintroduces a small amount of shared state purely for one-time-use/revocation tracking. This is a real, inherent tension in otherwise-stateless auth design: full statelessness and instant one-time-use/revocation guarantees pull in opposite directions, and most production systems accept a small sliver of shared state to get revocation right.

### Summary of this case study

- The *shape* of "password step → intermediate credential → OTP step" is a legitimate, common pattern (pre-authentication token).
- The scaling bug isn't in the pattern — it's in storing the intermediate value in a single server's local memory.
- Fix: either move it to shared infrastructure (Redis) or make it stateless (a short-lived, purpose-scoped JWT).
- Either way, scope the token/session to its specific purpose, and enforce single use to prevent replay.

