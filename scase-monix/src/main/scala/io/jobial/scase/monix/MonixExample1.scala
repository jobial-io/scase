package io.jobial.scase.monix

import cats.effect.IO
import cats.effect.IO.ioEffect
import cats.effect.IO.sleep
import io.jobial.sclap.CommandLineApp
import monix.execution.Scheduler.Implicits.global
import java.time.Instant.now
import scala.concurrent.duration._

object MonixExample1 extends CommandLineApp {

  def tick(source: Source[IO, Int], c: Int, delay: FiniteDuration): IO[Unit] =
    source.push(c) >> sleep(delay) // >> tick(source, c + 1, delay)

  def run =
    for {
      source1 <- Source[IO, Int]
      _ <- tick(source1, 0, 1.second).start
      r <- toIO(for {
        i1 <- source1
      } yield println(now + " " + i1))
      _ <- IO(println("finished"))
    } yield r

}
