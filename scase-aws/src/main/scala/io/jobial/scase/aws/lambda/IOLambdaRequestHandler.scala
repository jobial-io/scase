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
package io.jobial.scase.aws.lambda

import cats.effect._
import io.jobial.scase.core.RequestHandler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

abstract class IOLambdaRequestHandler[REQ, RESP]
  extends LambdaRequestHandler[IO, REQ, RESP] {

  implicit lazy val cs = IO.contextShift(ExecutionContext.global)

  implicit lazy val concurrent = IO.ioConcurrentEffect(cs)

  def runResult(result: IO[_]) =
    result.unsafeRunSync  
}