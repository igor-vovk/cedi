package me.ivovk.cedi

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import me.ivovk.cedi.syntax.*
import org.scalatest.flatspec.AnyFlatSpec

import scala.reflect.ClassTag

//noinspection TypeAnnotation
class AllocatorTest extends AnyFlatSpec {

  trait ctx {
    class CapturingAllocationListener extends AllocationLifecycleListener[IO] {
      var allocations: Seq[String] = Seq.empty
      var shutdowns: Seq[String]   = Seq.empty

      override def onInit[A: ClassTag](resource: A): IO[Unit] = IO {
        allocations = allocations :+ resource.toString
      }
      override def onShutdown[A: ClassTag](resource: A): IO[Unit] = IO {
        shutdowns = shutdowns :+ resource.toString
      }
    }

    object TestDependencies {
      def create(): (Resource[IO, TestDependencies], CapturingAllocationListener) = {
        val listener = new CapturingAllocationListener
        val deps     = Allocator.create[IO]()
          .map(_.withListener(listener))
          .map(TestDependencies(using _))

        (deps, listener)
      }
    }

    class TestDependencies(using AllocatorIO) {

      // Allocate resources using the Allocator syntax
      lazy val testResourceA: String = cedi {
        Resource.pure("resourceA")
      }

      // Allocate resources that depend on other resources using direct method
      lazy val testResourceB: String = cedi {
        Resource.pure(s"resourceB, depends on $testResourceA")
      }

    }
  }

  "Allocator" should "allocate a resource" in new ctx {
    val (testDependencies, listener) = TestDependencies.create()

    val testResource = testDependencies
      .use(deps => IO.pure(deps.testResourceA))
      .unsafeRunSync()

    assert(testResource == "resourceA")

    assert(listener.allocations == Seq("resourceA"))
    assert(listener.shutdowns == Seq("resourceA"))
  }

  it should "allocate resources in the correct order" in new ctx {
    val (testDependencies, listener) = TestDependencies.create()

    testDependencies
      .use(deps => IO.pure(deps.testResourceB))
      .unsafeRunSync()

    assert(listener.allocations == Seq("resourceA", "resourceB, depends on resourceA"))
    assert(listener.shutdowns == Seq("resourceB, depends on resourceA", "resourceA"))
  }

}
