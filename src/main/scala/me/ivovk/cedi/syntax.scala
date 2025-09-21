package me.ivovk.cedi

import cats.effect.IO

object syntax {

  type Allocator[F[_]] = me.ivovk.cedi.Allocator[F]
  val Allocator: me.ivovk.cedi.Allocator.type = me.ivovk.cedi.Allocator
  type AllocatorIO = me.ivovk.cedi.Allocator[IO]

  // Both `allocate` and `cedi` can be used
  def allocate: AllocateOps = new AllocateOps

  def cedi: AllocateOps = new AllocateOps

}
