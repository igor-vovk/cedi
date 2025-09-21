package me.ivovk.cedi

import cats.effect.*
import cats.effect.implicits.*
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import cats.implicits.*

import scala.reflect.ClassTag

object Allocator {

  def create[F[_]: Async](): Resource[F, Allocator[F]] =
    for
      dispatcher  <- Dispatcher.parallel[F]
      shutdownRef <- Ref.of(Async[F].unit).toResource
      allocator   <- {
        val acquire: F[Allocator[F]] = Async[F].delay {
          new Allocator(dispatcher, shutdownRef, NoOpListener[F])
        }
        val release: Allocator[F] => F[Unit] = _.shutdownAll

        Resource.make(acquire)(release)
      }
    yield allocator

  /**
   * When using this method you must call [[shutdownAll]] manually after you finished working with
   * dependencies.
   */
  def unsafeCreate(runtime: IORuntime): Allocator[IO] = {
    val (alloc, _) = create[IO]().allocated.unsafeRunSync()(using runtime)

    alloc
  }

  def apply[F[_]: Allocator]: Allocator[F] =
    implicitly[Allocator[F]]

}

class Allocator[F[_]: Sync] private (
  dispatcher: Dispatcher[F],
  shutdown: Ref[F, F[Unit]],
  listener: AllocationLifecycleListener[F],
) {

  def withListener(listener: AllocationLifecycleListener[F]): Allocator[F] =
    new Allocator(dispatcher, shutdown, listener)

  def allocate[A: ClassTag](resource: Resource[F, A]): A = {
    val fa = resource
      .allocated
      .flatMap { (acquired, release) =>
        listener.onInit(acquired) *>
          // Prepend release of this resource into the overall shutdown sequence
          shutdown.update(listener.onShutdown(acquired) *> release *> _) *>
          Sync[F].pure(acquired)
      }

    dispatcher.unsafeRunSync(fa)
  }

  def allocate[A: ClassTag](fa: F[A]): A =
    allocate(fa.toResource)

  def shutdownAll: F[Unit] =
    shutdown.getAndSet(Sync[F].unit).flatten

}
