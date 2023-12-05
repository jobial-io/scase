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
package io.jobial.scase.aws.client

import cats.effect.Concurrent
import cats.implicits._
import io.jobial.sprint.logging.Logging
import io.jobial.sprint.util.CatsUtils

import scala.collection.JavaConverters.asScalaBufferConverter

trait AwsClient[F[_]] extends CatsUtils[F] with Logging[F] {

  def getPaginatedResult[R, A](
    f: Option[String] => F[R]
  )(
    getResultList: R => java.util.List[A],
    getNextToken: R => String,
    limit: Int,
    nextToken: Option[String] = None
  )(implicit awsContext: AwsContext, concurrent: Concurrent[F]): F[Vector[A]] =
    for {
      r <- f(nextToken)
      l = getResultList(r).asScala.toVector
      rest <- Option(getNextToken(r)) match {
        case Some(token) =>
          val remaining = limit - l.size
          if (remaining > 0)
            getPaginatedResult(f)(getResultList, getNextToken, remaining, Some(token))
          else
            pure(List())
        case None =>
          pure(List())
      }
    } yield (l ++ rest).take(limit)

}
