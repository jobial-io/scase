package io.jobial.scase.monix

import cats.effect.IO
import cats.effect.IO.ioEffect
import cats.effect.IO.sleep
import io.jobial.sclap.CommandLineApp
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import java.time.Instant.now
import scala.concurrent.duration._

object MonixExample extends CommandLineApp {

  def tick(source: Source[IO, Int], c: Int, delay: FiniteDuration): IO[Unit] =
    source.push(c) >> sleep(delay) >> tick(source, c + 1, delay)

  def run =
    for {
      source1 <- Source[IO, Int]
      source2 <- Source[IO, Int]
      source3 <- Source[IO, Int]
      _ <- tick(source1, 0, 5.second).start
      _ <- tick(source2, 0, 10.second).start
      _ <- tick(source3, 0, 1.second).start
      r <- for {
        i1 <- source1.filter(_ % 2 == 0)
        i2 <- source2
        i3 <- source3
      } yield println(now + " " + i1 + " " + i2 + " " + i3)
    } yield r

}
