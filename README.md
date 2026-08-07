# cedi [tsedi]

[![Maven Central](https://img.shields.io/maven-central/v/me.ivovk/cedi_3?style=flat-square&color=green)](https://central.sonatype.com/artifact/me.ivovk/cedi_3)

**Lifecycle-safe application wiring for Cats Effect.**

As an application grows, wiring every dependency through one large `Resource` for-comprehension becomes difficult to
read, change, and split into modules. cedi lets you describe the same dependency graph with ordinary Scala classes and
lazy values while preserving Cats Effect resource lifecycles.

- Dependencies are initialized only when first accessed
- A dependency declared as a `lazy val` is initialized once
- `Resource` finalizers run in reverse allocation order
- Dependency groups can share a lifecycle or manage their own
- No macros, annotations, reflection-based discovery, or code generation

cedi is deliberately small. It is an application-wiring tool, not a framework that owns your application.

## Why cedi?

A small Cats Effect dependency graph is usually easy to express directly:

```scala
def application: Resource[IO, UserService] =
  for
    database   <- Database.connect
    repository = new UserRepository(database)
    service    = new UserService(repository)
  yield service
```

As the graph grows, this composition root can become a long, order-sensitive list. Splitting it into modules often means
passing many dependencies between `Resource` constructors.

With cedi, the graph is expressed as normal Scala object composition:

```scala
final class Dependencies(using AllocatorIO) {
  lazy val database: Database = provide(Database.connect)
  lazy val repository: UserRepository = new UserRepository(database)
  lazy val users: UserService = new UserService(repository)
}
```

Resources remain lazy and lifecycle-managed, but consumers receive ordinary values rather than `Resource` wrappers.

## Installation

cedi supports Scala `>= 3.3.x`.

```scala
libraryDependencies += "me.ivovk" %% "cedi" % "0.2.4"
```

## Quick start

This complete example uses small placeholder database and service implementations:

```scala
import cats.effect.{IO, IOApp, Resource}
import me.ivovk.cedi.syntax.*

final class Database

object Database {
  def connect: Resource[IO, Database] =
    Resource.make(IO(new Database))(_ => IO.println("Closing database"))
}

final class UserRepository(database: Database)

final class UserService(repository: UserRepository) {
  def run: IO[Unit] = IO.println("Running application")
}

object Dependencies {
  def create: Resource[IO, Dependencies] =
    Allocator.create[IO]().map(Dependencies(using _))
}

final class Dependencies(using AllocatorIO) {
  // Resource[IO, Database] is allocated on first access. Its finalizer is
  // registered with the Allocator.
  lazy val database: Database = provide(Database.connect)

  // Plain dependencies are constructed normally.
  lazy val repository: UserRepository = new UserRepository(database)
  lazy val users: UserService = new UserService(repository)
}

object Main extends IOApp.Simple {
  override def run: IO[Unit] =
    Dependencies.create.use(_.users.run)
}
```

When `Dependencies.create.use` finishes, the allocator releases every resource that was initialized. Resources that were
never accessed are never acquired.

`provide` also accepts an `F[A]`:

```scala
lazy val config: Config = provide(loadConfig[IO])
```

`provide`, `allocate`, and `cedi` are equivalent names, so you can use whichever reads best in your dependency object.

## How it works

`Allocator.create[F]()` returns a `Resource[F, Allocator[F]]`. A call to `provide`:

1. Runs the supplied `Resource[F, A]` or `F[A]`
2. Returns the acquired `A` as a plain value
3. Registers the `Resource` finalizer with the allocator (`F[A]` has a no-op release)

Finalizers are registered in last-in, first-out order. If dependency `B` accesses dependency `A` while it is being
initialized, `B` is released before `A`.

Scala's `lazy val` supplies the initialize-once behavior. Using `def` instead allocates a new instance on every call, with
each finalizer still registered for shutdown.

### The deliberate trade-off

`provide` acquires a dependency synchronously when the expression is evaluated so it can return a plain `A`. This makes
the composition root concise, but it also means acquisition failures surface while that value is being initialized.

Use cedi for long-lived application dependencies at the composition boundary. Keep short-lived, request-scoped, or
business-logic resources explicit as `Resource` or `F`.

## Modular dependency graphs

Dependency groups can share one allocator. This gives the whole graph one lifecycle and is the simplest option in most
applications:

```scala
final class PersistenceDependencies(using AllocatorIO) {
  lazy val database: Database = provide(Database.connect)
}

final class Dependencies(using AllocatorIO) {
  val persistence = new PersistenceDependencies

  lazy val repository: UserRepository =
    new UserRepository(persistence.database)
  lazy val users: UserService =
    new UserService(repository)
}
```

A module can also own a separate allocator. Allocate the module itself with `provide` so its entire lifecycle is nested
inside its parent:

```scala
object PersistenceDependencies {
  def create: Resource[IO, PersistenceDependencies] =
    Allocator.create[IO]().map(PersistenceDependencies(using _))
}

final class PersistenceDependencies(using AllocatorIO) {
  lazy val database: Database = provide(Database.connect)
}

final class Dependencies(using AllocatorIO) {
  lazy val persistence: PersistenceDependencies =
    provide(PersistenceDependencies.create)

  lazy val repository: UserRepository =
    new UserRepository(persistence.database)
  lazy val users: UserService =
    new UserService(repository)
}
```

## Debugging allocation order

Attach a `LoggingAllocationListener` to log resources as they are initialized and released:

```scala
import cats.effect.{IO, Resource}
import me.ivovk.cedi.{Allocator, LoggingAllocationListener}

val allocator: Resource[IO, Allocator[IO]] =
  Allocator
    .create[IO]()
    .map(_.withListener(new LoggingAllocationListener[IO]))
```

## Background

cedi grew out of the article
[Dependency Injection with Cats Effect Resource Monad](https://medium.com/@ivovk/dependency-injection-with-cats-effect-resource-monad-ad7cd47b977).
