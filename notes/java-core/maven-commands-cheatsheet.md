# Maven — What It Is & When to Use Each Command

## What is Maven, actually?

Maven is a **build automation and dependency management tool** for Java projects.

Break that down:

- **Build automation** — it takes your `.java` source files and turns them into something runnable (a `.class` folder, a `.jar`, a `.war`) without you manually calling `javac` yourself.
- **Dependency management** — your `pom.xml` lists the libraries you need (Spring Boot, Jackson, etc.), and Maven downloads them from a remote repository and wires them onto your classpath. You never manually download a `.jar` and drop it in a folder.

Think of Maven as the thing standing between "I wrote some Java files" and "I have a working, deployable application with all its libraries attached."

---

## The core concept: the Build Lifecycle

This is the one idea that makes everything else make sense.

Maven doesn't have a random list of unrelated commands. It has a **lifecycle** — a fixed, ordered sequence of phases. When you run a phase, Maven runs that phase **and every phase before it**, automatically.

```
validate → compile → test → package → verify → install → deploy
```

So if you run `mvn install`, Maven silently does `validate`, `compile`, `test`, `package`, `verify` first, *then* `install`. You never run these individually in normal day-to-day work — you just pick the last phase you need, and Maven handles the rest.

This is why the answer to "can I run `package` without compiling?" is **no** — `compile` sits earlier in the chain, so it always runs first. It's not optional, it's structural.

---

## The phases, explained

| Phase | What it actually does |
|---|---|
| `validate` | Checks the project structure/`pom.xml` is correct before doing anything else |
| `compile` | Turns `.java` files into `.class` files (in `target/classes`) |
| `test` | Runs unit tests (JUnit/TestNG) against the compiled code |
| `package` | Bundles compiled code into a distributable `.jar` or `.war` (in `target/`) |
| `verify` | Runs integration tests / quality checks before install |
| `install` | Copies the packaged `.jar`/`.war` into your **local repo** (`~/.m2/repository`) so other local projects can depend on it |
| `deploy` | Pushes the final package to a **remote/shared repo** (Nexus, Artifactory) for the team |

`clean` is separate — it's not part of this lifecycle chain at all. It just deletes the `target/` folder. More on that below.

---

## `clean` — the odd one out

`mvn clean` isn't part of the build lifecycle above. It belongs to its own tiny lifecycle whose only job is: delete `target/`.

**Why you need it:** occasionally Maven's incremental build gets confused — stale `.class` files linger, or you switch git branches and old compiled output doesn't match the new source. `clean` wipes the slate so the next build starts from zero.

Because it's separate, you chain it in front of whatever phase you actually want:

```bash
mvn clean install
mvn clean package
```

---

## When to use each command

| Command | Use it when... |
|---|---|
| `mvn compile` | You just want a fast syntax/compile-error check. Skips tests and packaging — quickest feedback loop. |
| `mvn test` | You changed code and want to confirm you didn't break existing unit tests. |
| `mvn package` | You want a runnable `.jar`/`.war` — to run locally or hand to someone. Most standalone apps stop here. |
| `mvn install` | You're working on a **multi-module project or shared library**, and another project on your machine needs to pull in your latest changes as a dependency. |
| `mvn deploy` | You're ready to publish the artifact to a team-shared/remote repository. |
| `mvn clean ...` | You changed branches, or the app is behaving like your latest changes aren't there. Prefix it to force a fresh build. |
| `mvn validate` | Rare to run manually — mostly useful in CI to fail fast before wasting time compiling a broken `pom.xml`. |
| `mvn verify` | Rare to run manually outside CI — used when integration tests gate the build before packaging is "trusted." |

---

## Do you need to run every command separately?

**No.** This is the single biggest misconception. Because phases run in sequence, you only ever type the *last* phase you need:

- Building a standalone Spring Boot app to run locally? → `mvn clean package` is enough.
- Working on a shared library another local project depends on? → `mvn clean install`.
- Just checking your code compiles, fast? → `mvn compile`.

You don't manually chain `validate → compile → test → package` — Maven already does that for you the moment you ask for `package`.

---

## Is `mvn install` only for shared libraries?

Effectively, yes. Its entire purpose is putting the artifact into your **local repo** (`~/.m2/repository`) so *other local projects* can find and import it.

- **Standalone app, nothing depends on it** → `mvn clean package` is enough. `install` does nothing useful for you here.
- **Multi-module setup** (e.g. `common-lib` that `bvn-service` depends on) → you must `mvn clean install` on `common-lib` first, or `bvn-service` won't be able to resolve it as a dependency.

---

## Real-world analogy

- `compile` = spell-checking your essay.
- `test` = having someone read it and flag logical holes.
- `package` = printing and binding it into a book.
- `install` = putting that book on **your own** shelf so you (and only you, locally) can reference it in another project.
- `deploy` = publishing it so the whole team/library can grab a copy.
- `clean` = tearing up all your drafts and starting the page from scratch.

---

## Useful extras beyond the lifecycle

| Command | What it's for |
|---|---|
| `mvn package -DskipTests` | Package fast without running tests — common when you just need a quick local build |
| `mvn dependency:tree` | Prints a tree of all dependencies — essential for debugging version conflicts |
| `mvn -pl <module> -am install` | In a multi-module project, build only one module (`-pl`) plus the modules it depends on (`-am`) |

---

## Quick summary

- Maven = build automation + dependency management for Java.
- It runs a fixed **lifecycle**: `validate → compile → test → package → verify → install → deploy`. Running a later phase auto-runs everything before it.
- `clean` is separate — it just deletes `target/`, prefix it when the build feels stale.
- Day-to-day, you almost always type **one** command, not several: `compile` (quick check), `package` (runnable artifact), or `install` (share with other local projects).
- `install` only matters if another local project needs to depend on this one — otherwise `package` is the finish line.
- `deploy` is for pushing to a team-shared remote repo, not local use.
