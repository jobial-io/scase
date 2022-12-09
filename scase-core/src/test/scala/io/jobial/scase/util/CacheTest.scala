package io.jobial.scase.util

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.IO.pure
import cats.effect.IO.sleep
import io.jobial.scase.core.test.ScaseTestHelper
import org.scalatest.flatspec.AsyncFlatSpec
import scala.concurrent.duration.DurationInt

class CacheTest extends AsyncFlatSpec with ScaseTestHelper {

  "cache expiry" should "work" in {
    for {
      cache <- Cache[IO, String, String](2.seconds, 1)
      expiryA <- Deferred[IO, Unit]
      aValue <- cache.getOrCreate("a", pure("1"), { (k, v) => expiryA.complete().map(_ => ()) })
      _ = assert(aValue === "1")
      size <- cache.size
      _ = assert(size === 1)
      _ <- sleep(1.second)
      expiryB <- Deferred[IO, Unit]
      bValue <- cache.getOrCreate("b", pure("2"), { (k, v) => expiryB.complete().map(_ => ()) })
      _ = assert(bValue === "2")
      size <- cache.size
      _ = assert(size === 2)
      _ <- sleep(1.second + 1.milli)
      expiryC <- Deferred[IO, Unit]
      cValue <- cache.getOrCreate("c", pure("3"), { (k, v) => expiryC.complete().map(_ => ()) })
      _ = assert(cValue === "3")
      size <- cache.size
      _ = assert(size === 2)
      _ <- expiryA.get
      _ <- expiryB.tryGet.map(r => assert(r === None))
      _ <- expiryC.tryGet.map(r => assert(r === None))
    } yield succeed
  }
}
