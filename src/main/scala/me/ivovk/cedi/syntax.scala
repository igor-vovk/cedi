package me.ivovk.cedi

import cats.effect.IO

object syntax {

  type Allocator[F[_]] = me.ivovk.cedi.Allocator[F]
  val Allocator: me.ivovk.cedi.Allocator.type = me.ivovk.cedi.Allocator
  type AllocatorIO = me.ivovk.cedi.Allocator[IO]

  // `allocate`, `provide` or `cedi` can be used interchangeably depending on your preference
  def allocate: AllocateOps = new AllocateOps

  def provide: AllocateOps = new AllocateOps

  def cedi: AllocateOps = new AllocateOps

}
