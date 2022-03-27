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
package io.jobial.scase.core

import cats.effect.IO
import cats.tests.StrictCatsEquality
import org.scalactic.source
import org.scalatest.Assertion
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.AsyncFlatSpec

import java.util.concurrent.Executors.newCachedThreadPool
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.fromExecutor
import scala.reflect.ClassTag

trait ScaseTestHelper {
  this: AsyncFlatSpec =>
  
  val ec = fromExecutor(newCachedThreadPool)
  
  implicit val contextShift = IO.contextShift(ec)

  implicit val timer = IO.timer(ec)

  implicit def runIOResult(r: IO[Assertion]) = r.unsafeToFuture

  implicit def fromEitherResult(r: Either[Throwable, Assertion]) = runIOResult(IO.fromEither(r))

  def recoverToSucceededIf[T <: AnyRef](io: IO[Any])(implicit classTag: ClassTag[T], pos: source.Position): IO[Assertion] =
    IO.fromFuture(IO(recoverToSucceededIf(io.unsafeToFuture())))

}
