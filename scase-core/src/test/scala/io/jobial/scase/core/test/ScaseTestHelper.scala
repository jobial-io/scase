/*
 * Copyright (c) 2020 Jobial OÜ. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package io.jobial.scase.core.test

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.jobial.scase.core.impl.TemporalEffect
import org.scalactic.source
import org.scalatest.Assertion
import org.scalatest.Succeeded
import org.scalatest.flatspec.AsyncFlatSpec
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

trait ScaseTestHelper {
  this: AsyncFlatSpec =>

  val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)

  val onGithub = sys.env.get("RUNNER_OS").isDefined

  val onMacOS = sys.props.get("os.name").map(_ === "Mac OS X").getOrElse(false)

  implicit def temporal = TemporalEffect[IO]

  implicit def runtime = IORuntime.global

  implicit def runIOResult(r: IO[Assertion]) = r.unsafeToFuture

  implicit def fromEitherResult(r: Either[Throwable, Assertion]) = runIOResult(IO.fromEither(r))

  def recoverToSucceededIf[T <: AnyRef](io: IO[Any])(implicit classTag: ClassTag[T], pos: source.Position): IO[Assertion] =
    IO.fromFuture(IO(recoverToSucceededIf(io.unsafeToFuture())))

  implicit def assertionsToIOAssert(l: IO[List[Assertion]]): IO[Assertion] =
    for {
      l <- l
    } yield assert(l.forall(_ === Succeeded))

  implicit def assertionsToFutureAssert(l: IO[List[Assertion]]) =
    runIOResult(l)
}
