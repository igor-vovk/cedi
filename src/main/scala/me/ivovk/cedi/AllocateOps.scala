package me.ivovk.cedi

import cats.effect.Resource

import scala.reflect.ClassTag

class AllocateOps {
  def apply[F[_]: Allocator, A: ClassTag](fa: F[A]): A =
    Allocator[F].allocate(fa)

  def apply[F[_]: Allocator, A: ClassTag](res: Resource[F, A]): A =
    Allocator[F].allocate(res)
}
