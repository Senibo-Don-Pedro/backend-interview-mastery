# The Servlet Filter Mechanism & Spring Security Filter Chain

## The Problem This Solves

Without any security layer, every endpoint in MPPS is wide open:

```java
@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    @PostMapping("/{id}/transfer")
    public ResponseEntity<?> transfer(@PathVariable UUID id, @RequestBody TransferRequest request) {
        // anyone, with no credentials, can call this right now
    }
}
```

You need something that intercepts **every single HTTP request**, before it reaches any controller, and answers: is this request allowed to proceed at all? Who is making it? Are they permitted to do this specific thing? That's not one check — it's a pipeline (CORS, CSRF, authentication, authorization...). Spring Security implements this pipeline as a **chain of filters**.

---

## Part 1 — What a Filter Actually Is (This Is NOT a Spring Concept)

This is the single most important thing to get straight: **`Filter` is a Java interface defined by the Servlet API** (`jakarta.servlet.Filter`), part of the Jakarta EE (formerly Java EE) web specification. It predates Spring Security by decades. Tomcat — the server embedded inside every Spring Boot app — understands and runs filters natively, with **zero Spring involvement required**.

```java
// Defined by the Servlet API spec, NOT by Spring
package jakarta.servlet;

public interface Filter {
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException;
}
```

### The mental model: Filter = Middleware

If you've used Express.js, this maps almost perfectly:

```javascript
// Express middleware
app.use((req, res, next) => {
    console.log('Request coming in:', req.url);
    next();  // pass control onward
});

app.use((req, res, next) => {
    if (!req.headers.authorization) {
        return res.status(401).send('Unauthorized');  // stop here, no next()
    }
    next();
});
```

```java
// Servlet filter — same pattern, different syntax
public class LoggingFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        System.out.println("Request coming in: " + request);
        chain.doFilter(request, response);  // pass control onward
    }
}
```

| Express middleware | Servlet filter |
|---|---|
| `app.use(fn)` registers it | Registered into a `SecurityFilterChain` / filter config |
| `next()` continues the chain | `chain.doFilter()` continues the chain |
| Not calling `next()` halts the request | Not calling `doFilter()` halts the request |
| Runs before your route handler | Runs before `DispatcherServlet` / your `@Controller` |
| Order of `app.use()` calls matters | Filter chain position matters (`addFilterBefore`, etc.) |
| Can inspect/modify req and res | Can inspect/modify `ServletRequest`/`ServletResponse` |

**Where the analogy needs precision:** Express middleware is a framework-level convention (Express invented it). Servlet filters are a spec-level mechanism — any servlet container (Tomcat, Jetty, etc.) understands them natively, no framework required. "A filter is Java's version of middleware" is a correct and useful shortcut — just don't say it's a Spring Security invention. It's a general interception pattern that Spring Security happens to fill with security logic, the same way Express middleware can hold logging, compression, or auth logic.

### A filter with nothing to do with security

```java
public class LoggingFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        System.out.println("Request coming in: " + request);
        chain.doFilter(request, response);   // continue to the next thing
        System.out.println("Response going out: " + response);
    }
}
```

Filters are general-purpose. You could write one for logging, compression, encoding, CORS headers — none of that is security-specific. Spring Security just happens to be a large, well-organized *application* of this pattern.

---

## Part 2 — Where Spring Security Plugs Into the Servlet World

```
Servlet API (Jakarta EE spec, ~1999)
   ↓ provides the Filter interface
Tomcat (the servlet container Spring Boot embeds)
   ↓ knows how to run a chain of Filters before any servlet
Spring Security
   ↓ writes many Filter implementations (CsrfFilter, AuthorizationFilter, etc.)
   ↓ registers them all into ONE chain via DelegatingFilterProxy
Your app
```

```
Client Request
     ↓
[Servlet Container - Tomcat]
     ↓
DelegatingFilterProxy   ← Spring Security's single entry point into the raw servlet filter mechanism
     ↓
FilterChainProxy        ← holds and manages Spring's own internal list of filters
     ↓
┌─────────────────────────────────────┐
│  SecurityFilterChain (ordered list)  │
│  1. SecurityContextHolderFilter      │
│  2. CorsFilter                       │
│  3. CsrfFilter                       │
│  4. YourCustomJwtFilter (added)      │
│  5. UsernamePasswordAuthFilter       │
│  6. ExceptionTranslationFilter       │
│  7. AuthorizationFilter              │
└─────────────────────────────────────┘
     ↓
DispatcherServlet
     ↓
Your @RestController
```

`DelegatingFilterProxy` is the bridge point: it's a plain Servlet `Filter` that Tomcat knows about natively. Its entire job is to hand off execution to Spring's internally-managed `FilterChainProxy`, which holds your actual `SecurityFilterChain`. This is the plug connecting the plain servlet world to Spring Security's world.

Every request passes through **every filter in this chain, in order**, before `DispatcherServlet` ever routes it to a `@Controller`. Each filter either continues the chain or halts it with a response (401/403/etc.).

---

## Part 3 — Walking Through the Real Filter Chain

### 1. `SecurityContextHolderFilter`
Sets up an empty `SecurityContext` for this request — a slot where "who is this user" will get filled in later.

```java
// conceptually:
app.use((req, res, next) => {
    req.securityContext = {};  // empty slot, ready to be filled
    next();
});
```

### 2. `CorsFilter`
Checks whether the request's origin is allowed to talk to the API at all (browser cross-origin rules). Blocks disallowed origins here, before authentication is even considered.

### 3. `CsrfFilter`
Checks for a CSRF token on state-changing requests (POST/PUT/DELETE) to stop a malicious site from tricking a logged-in user's browser into making requests on their behalf. **CSRF is a session-cookie attack** — it doesn't apply to stateless JWT APIs, which is why MPPS disables it:

```java
.csrf(csrf -> csrf.disable())
```

### 4. Custom `JwtAuthenticationFilter` (MPPS capstone — this is where your own filter goes)
Job: **authentication only** — figure out *who* is making the request.

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                      HttpServletResponse response,
                                      FilterChain filterChain) throws ServletException, IOException {

        String token = extractTokenFromHeader(request);

        if (token != null && jwtService.isValid(token)) {
            Authentication auth = jwtService.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
            // fills the empty slot SecurityContextHolderFilter set up in step 1
        }

        filterChain.doFilter(request, response);  // ALWAYS continue, even with no valid token
    }
}
```

Critically: it **always calls `filterChain.doFilter()`**, even without a valid token. It doesn't reject anything itself — it just leaves the context empty if it can't identify the caller. Rejection is a different filter's job, later in the chain.

### 5. `UsernamePasswordAuthenticationFilter`
Built-in filter for traditional login flows (`POST /login` with username/password body). In a JWT-only API like MPPS, this effectively never matches and passes through — but it's still part of the default chain unless explicitly removed.

### 6. `ExceptionTranslationFilter`
Catches security exceptions thrown further down the chain (like `AccessDeniedException`) and converts them into proper HTTP responses (401/403) instead of a raw 500. Conceptually similar to Express error-handling middleware: `app.use((err, req, res, next) => {...})` — it wraps everything downstream of it.

### 7. `AuthorizationFilter`
The one that actually **rejects** requests. Looks at whatever identity landed in the `SecurityContext` (or its absence) and checks it against your rules:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .anyRequest().authenticated()
)
```

- Nobody authenticated + endpoint requires auth → **401**
- Someone authenticated but missing required role/permission → **403**

This is deliberately the *last* security filter — everything before it exists to gather information; this one acts on it.

---

## Part 4 — Why Order Is Non-Negotiable

A filter can only rely on information set by filters that ran **before** it — never after. This is why:

- `JwtAuthenticationFilter` must run before `AuthorizationFilter` (authorization needs to know who you are before deciding if you're allowed).
- `SecurityContextHolderFilter` must run before your JWT filter (there needs to be a context object to write the identity into).

If the order were wrong — say, `AuthorizationFilter` ran before your JWT filter — **every request would be rejected as unauthenticated**, because at the moment authorization checks, nobody has told it who's calling yet.

---

## Part 5 — Registering and Ordering Your Own Custom Filter

You don't need to override or modify any built-in Spring Security filter to add your own logic. Write a standalone filter and slot it into position **relative to an existing filter class** — Spring Security doesn't let you specify a raw numeric position, because the internal ordering of built-ins isn't meant to be depended on directly.

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http, MyCustomFilter myFilter) throws Exception {
    http
        // run BEFORE a specific existing filter
        .addFilterBefore(myFilter, UsernamePasswordAuthenticationFilter.class)

        // run AFTER a specific existing filter
        .addFilterAfter(myFilter, JwtAuthenticationFilter.class)

        // run AT the same position as an existing filter (replaces its slot — rare, use carefully)
        .addFilterAt(myFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

### Writing a standalone custom filter (no relation to any existing filter needed)

```java
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                      HttpServletResponse response,
                                      FilterChain filterChain) throws ServletException, IOException {

        long start = System.currentTimeMillis();
        filterChain.doFilter(request, response);  // let everything downstream run first
        long duration = System.currentTimeMillis() - start;

        System.out.printf("%s %s took %dms%n", request.getMethod(), request.getRequestURI(), duration);
    }
}
```

Register it relative to whatever makes sense:

```java
.addFilterBefore(new RequestLoggingFilter(), SecurityContextHolderFilter.class)
// runs first, before Spring Security has even set up the context —
// logs literally every request, security-relevant or not
```

### Why `OncePerRequestFilter` instead of implementing `Filter` directly

The raw Servlet `Filter` interface can, in certain edge cases (internal server-side forwards, error dispatches), get invoked more than once for a single logical incoming request. `OncePerRequestFilter` is a Spring-provided base class that guarantees `doFilterInternal()` runs **exactly once per request** — which is what you want almost always. This is why virtually every custom Spring Security filter extends it instead of implementing `Filter` raw.

### Actually wiring your JWT filter (from the earlier config)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
```

`addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)` isn't a suggestion — it's a hard ordering requirement, for the exact reason covered in Part 4.

---

## Interview Gotchas — Summary

- **"Is `Filter` a Spring concept?"** → No. It's defined by the Servlet API (Jakarta EE spec). Spring Security reuses it; it didn't invent it. Tomcat runs filters with zero Spring involvement.
- **"What's the Node.js/Express equivalent of a filter?"** → Middleware. `chain.doFilter()` ≈ `next()`. Not calling it halts the request in both worlds.
- **"How does a filter reject a request?"** → It simply never calls `chain.doFilter()` and writes a response directly (e.g., sets status 401 and returns).
- **"Why does `JwtAuthenticationFilter` always call `filterChain.doFilter()` even with no token?"** → Its job is authentication only, not rejection. It records "I don't know who this is" by leaving the `SecurityContext` empty; `AuthorizationFilter` later decides whether that's acceptable for this endpoint.
- **"How do you add your own filter to the chain?"** → Write a standalone class (usually extending `OncePerRequestFilter`), then register it with `addFilterBefore` / `addFilterAfter` / `addFilterAt`, positioned relative to an existing filter class — never a raw index.
- **"Why extend `OncePerRequestFilter` instead of implementing `Filter` directly?"** → Guarantees the filter runs exactly once per request, avoiding edge-case double execution during internal forwards/error dispatches.
- **"Why does order in the chain matter so much?"** → Filters can only use information set by filters that ran before them. Authentication must precede authorization, or every request gets rejected as unauthenticated.
- **"Why does MPPS disable CSRF?"** → CSRF protection defends against session-cookie-based attacks. MPPS is a stateless JWT API with no session cookies, so the attack doesn't apply.

---

## Quick Summary

- **Filter** = a Servlet API interface (`jakarta.servlet.Filter`), general-purpose request/response interception, unrelated to Spring or security by default. Conceptually identical to middleware in Express/Node.
- **`chain.doFilter()`** continues to the next filter; not calling it halts the request — same as `next()` in Express.
- **Spring Security** writes many `Filter` implementations for security purposes and chains them via `DelegatingFilterProxy` → `FilterChainProxy` → `SecurityFilterChain`.
- The core chain, in order: `SecurityContextHolderFilter` → `CorsFilter` → `CsrfFilter` → (your JWT filter) → `UsernamePasswordAuthenticationFilter` → `ExceptionTranslationFilter` → `AuthorizationFilter`.
- **Authentication filters** (like your JWT filter) figure out *who* — they never reject, they just populate or leave empty the `SecurityContext`.
- **`AuthorizationFilter`** is the actual gatekeeper — it rejects with 401/403 based on what earlier filters established.
- Order is non-negotiable: a filter can only depend on state set by filters before it.
- **Custom filters**: write a standalone class (extend `OncePerRequestFilter`), register with `addFilterBefore`/`addFilterAfter`/`addFilterAt` relative to an existing filter class — no need to override anything built-in.

## Code Reference (MPPS)

- `SecurityConfig` (`@EnableWebSecurity`, `SecurityFilterChain` bean) — where the entire chain and custom filter position are configured
- `JwtAuthenticationFilter` — the custom authentication filter to be built in the capstone, extending `OncePerRequestFilter`
- `.csrf(csrf -> csrf.disable())` — justified by MPPS being a stateless JWT API
- `.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))` — reinforces why CSRF and session-based filters are irrelevant here
