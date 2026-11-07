package me.ivovk.cedi

import cats.effect.*
import cats.effect.implicits.*
import cats.effect.std.Dispatcher
import cats.implicits.*

import scala.reflect.ClassTag

object Allocator {

  def newBuilder[F[_]: Async]: AllocatorBuilder[F] =
    new AllocatorBuilder(
      listener = NoOpListener[F]
    )

  def apply[F[_]: Allocator]: Allocator[F] =
    implicitly[Allocator[F]]

  final class AllocatorBuilder[F[_]: Async] private[cedi] (
    listener: AllocationLifecycleListener[F]
  ) {

    private def copy(
      listener: AllocationLifecycleListener[F]
    ): AllocatorBuilder[F] =
      new AllocatorBuilder[F](listener)

    def withListener(listener: AllocationLifecycleListener[F]): AllocatorBuilder[F] =
      copy(listener)

    def create(): Resource[F, Allocator[F]] =
      for
        dispatcher  <- Dispatcher.parallel[F]
        shutdownRef <- Ref.of(Async[F].unit).toResource
        allocator   <- {
          val acquire: F[Allocator[F]] = Async[F].delay {
            new Allocator(dispatcher, shutdownRef, listener)
          }
          val release: Allocator[F] => F[Unit] =
            _ => shutdownRef.getAndSet(Async[F].unit).flatten

          Resource.make(acquire)(release)
        }
      yield allocator

    def create[A](dependencies: Allocator[F] ?=> A): Resource[F, A] =
      create().map(alo => dependencies(using alo))

  }

}

class Allocator[F[_]: Sync] private (
  dispatcher: Dispatcher[F],
  shutdown: Ref[F, F[Unit]],
  listener: AllocationLifecycleListener[F],
) {

  def allocate[A: ClassTag](resource: Resource[F, A]): A = {
    val fa = resource.allocated.flatMap { (acquired, release) =>
      listener.onInit(acquired) *>
        // Prepend release of this resource into the overall shutdown sequence
        shutdown.update(listener.onShutdown(acquired) *> release *> _) *>
        Sync[F].pure(acquired)
    }

    dispatcher.unsafeRunSync(fa)
  }

  def allocate[A: ClassTag](fa: F[A]): A =
    allocate(fa.toResource)

}
