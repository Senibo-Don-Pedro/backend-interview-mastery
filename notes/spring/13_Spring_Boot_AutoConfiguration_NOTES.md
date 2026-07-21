# Spring Boot Auto-Configuration

## The Problem This Solves

A plain Spring app (not Boot) using JPA + Postgres requires manually wiring a `DataSource`, an `EntityManagerFactory`, and a `PlatformTransactionManager` before you write any business logic:

```java
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://localhost:5432/mpps");
        ds.setUsername("postgres");
        ds.setPassword("password");
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.mpps.entity");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        return em;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        JpaTransactionManager tm = new JpaTransactionManager();
        tm.setEntityManagerFactory(emf);
        return tm;
    }
    // ...and this is before configuring RabbitMQ, Jackson, an embedded server, etc.
}
```

Nearly every Spring Boot project needs the same boilerplate for the same common tools. Writing it by hand every time is repetitive and error-prone.

**Spring Boot's answer:** ship a large library of pre-written `@Configuration` classes for common scenarios, each **conditional** — only activating if it makes sense for this specific app, and only if you haven't already configured that thing yourself. This is auto-configuration.

---

## What "Starters" Actually Are

Adding this to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

A starter is **not magic** — it's a dependency-only artifact (no code of its own) that pulls in a curated, version-compatible bundle of libraries: Hibernate, Spring Data JPA, HikariCP (connection pool), JDBC driver machinery, etc. Its job is purely to save you from manually tracking every individual library "JPA support" requires.

---

## How Auto-Configuration Actually Triggers — `@Conditional`

Simplified version of Spring Boot's real `DataSourceAutoConfiguration`:

```java
@Configuration
@ConditionalOnClass(DataSource.class)          // only if DataSource.class is on the classpath
@ConditionalOnMissingBean(DataSource.class)    // only if YOU haven't already defined your own DataSource bean
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceAutoConfiguration {

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }
}
```

### The key conditional annotations

```java
@ConditionalOnClass(SomeClass.class)
// Only activates if SomeClass is present on the classpath.
// This is how adding a starter "switches on" a feature —
// the classes it brings in satisfy the condition.

@ConditionalOnMissingBean(SomeType.class)
// Only activates if YOU haven't already defined a bean of that type.
// The escape hatch: define your own DataSource bean, and
// Spring Boot's auto-configured one silently backs off.

@ConditionalOnProperty(name = "some.property", havingValue = "true")
// Only activates if a specific application.properties value is set.

@ConditionalOnMissingClass("some.Class")
// Only activates if a class is ABSENT (inverse of ConditionalOnClass)
```

This is why adding `spring-boot-starter-data-jpa` is enough for `DataSource`, `EntityManagerFactory`, and `JpaTransactionManager` beans to just exist: the starter puts the required classes on the classpath → `@ConditionalOnClass` is satisfied → (assuming no manual bean of that type already exists) the auto-configuration activates and wires everything using sensible defaults.

---

## Tracing It in MPPS

`application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/mpps
spring.datasource.username=postgres
spring.datasource.password=password
```

These values are bound onto a `DataSourceProperties` object via `@ConfigurationProperties(prefix = "spring.datasource")`. `DataSourceAutoConfiguration` then uses that populated object to build the actual `DataSource` bean. You never wrote a `@Bean` method for this — the auto-configuration class did it, conditionally, because `spring-boot-starter-data-jpa` put the right classes on the classpath.

---

## How to See It For Real — Debug Report

Add to `application.properties`:

```properties
debug=true
```

On startup, Boot prints a full report of what fired and why:

```
Positive matches:
-----------------
   DataSourceAutoConfiguration matched:
      - @ConditionalOnClass found required class 'javax.sql.DataSource' (OnClassCondition)

Negative matches:
-----------------
   RabbitAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'org.springframework.amqp.rabbit.core.RabbitTemplate' (OnClassCondition)
```

Genuinely useful for debugging "why isn't my bean being created" — it names the exact condition that failed.

---

## Interview Gotcha: How Does Boot Even Find These Classes?

Older Spring Boot versions listed every auto-configuration class in `META-INF/spring.factories`. **Since Spring Boot 2.7+**, it's:

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

A plain text file, one fully-qualified class name per line. `@SpringBootApplication` — specifically the `@EnableAutoConfiguration` piece of it — tells Spring to read this file and evaluate every listed class's conditions at startup.

Knowing this file exists, and that `@EnableAutoConfiguration` is what triggers reading it, signals real understanding of the mechanism rather than just recognizing the annotation.

---

## What `@SpringBootApplication` Actually Is

It's not a single feature — it's a **meta-annotation** that bundles three separate annotations into one:

```java
@SpringBootApplication
public class MppsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MppsApplication.class, args);
    }
}
```

is functionally identical to:

```java
@SpringBootConfiguration   // a specialized @Configuration — marks this class as a source of bean definitions
@EnableAutoConfiguration   // triggers auto-configuration (reads AutoConfiguration.imports, as above)
@ComponentScan             // scans this package + sub-packages for @Component/@Service/@Repository/@Controller
public class MppsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MppsApplication.class, args);
    }
}
```

### What each piece does

```java
@SpringBootConfiguration
// A specialized version of @Configuration. Marks this class as a
// source of @Bean definitions and eligible for component scanning itself.

@EnableAutoConfiguration
// The trigger described above — reads
// META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
// and evaluates every listed class's @Conditional annotations.

@ComponentScan
// Scans the package containing this class, and every sub-package, for
// @Component/@Service/@Repository/@Controller/@RestController, registering
// them as beans.
```

**Why package placement matters:** `@ComponentScan` only scans the package the main class lives in, plus sub-packages. If `MppsApplication` sits in `com.mpps`, everything under `com.mpps.wallet`, `com.mpps.transaction`, etc. gets picked up. A `@Service` living outside that tree (a sibling or parent package) simply won't be found — and Spring won't throw an error. The bean silently doesn't exist, which shows up later as a confusing dependency-injection failure with no obvious cause.

**Interview gotcha:** "Could you use `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan` separately instead of `@SpringBootApplication`?" — Yes, it's functionally identical. `@SpringBootApplication` exists purely for convenience. Knowing it's three annotations bundled together is the kind of internals knowledge that separates "I used Spring Boot" from "I understand what Spring Boot is doing."

---

## Interview Gotchas — Summary

- **"What is a starter, mechanically?"** → A dependency-only artifact with no code of its own — it exists purely to pull in a curated, compatible bundle of libraries.
- **"How does adding a dependency 'turn on' a feature?"** → The dependency puts specific classes on the classpath. `@ConditionalOnClass` checks for those classes; if found, the auto-configuration activates.
- **"What if I want my own DataSource instead of Boot's default?"** → Just define your own `@Bean` of that type. `@ConditionalOnMissingBean` detects it and Boot's auto-configuration backs off automatically — no need to disable anything explicitly.
- **"How does Boot discover which auto-configuration classes exist?"** → Reads `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (pre-2.7: `spring.factories`), triggered by `@EnableAutoConfiguration` inside `@SpringBootApplication`.
- **"How do you debug an auto-configuration that isn't firing?"** → Set `debug=true` in `application.properties` and read the positive/negative match report — it names the exact failed condition.

---

## Quick Summary

- Auto-configuration = pre-written `@Configuration` classes that activate conditionally, saving you from manual boilerplate wiring for common tools (DataSource, JPA, RabbitMQ, Jackson, etc.).
- **Starters** are dependency-only artifacts that bundle the libraries needed for a feature — they contain no logic themselves.
- **`@ConditionalOnClass`** activates a config if a class is present on the classpath (usually because a starter added it).
- **`@ConditionalOnMissingBean`** backs off if you've already defined your own bean of that type — your explicit configuration always wins.
- **`debug=true`** prints a full report of which auto-configurations matched or didn't, and why — the primary debugging tool for this system.
- Discovery mechanism: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, read because of `@EnableAutoConfiguration` (bundled inside `@SpringBootApplication`).
- **`@SpringBootApplication`** = `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`, bundled as one meta-annotation for convenience. The `@ComponentScan` piece means your main class's package location determines what gets picked up as a bean.

## Code Reference (MPPS)

- `pom.xml` — `spring-boot-starter-data-jpa`, `spring-boot-starter-amqp`, etc. — each one is the trigger for a corresponding auto-configuration class
- `application.properties` — `spring.datasource.*` properties bound via `DataSourceProperties`, consumed by `DataSourceAutoConfiguration`
- No manual `DataSource`/`EntityManagerFactory`/`PlatformTransactionManager` `@Bean` definitions exist in MPPS — confirms auto-configuration is doing this wiring
- `MppsApplication` (the `@SpringBootApplication`-annotated main class) — its package location is what determines `@ComponentScan`'s reach across the whole codebase
