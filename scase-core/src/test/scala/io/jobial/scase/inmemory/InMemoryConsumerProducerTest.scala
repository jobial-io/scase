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
package io.jobial.scase.inmemory

import cats.effect.IO
import cats.effect.concurrent.Deferred
import io.jobial.scase.core.{ScaseTestHelper, TestRequest1}
import io.jobial.scase.marshalling.serialization._
import org.scalatest.flatspec.AsyncFlatSpec

class InMemoryConsumerProducerTest extends AsyncFlatSpec with ScaseTestHelper {

  "request-response service" should "reply" in {
    val request = TestRequest1("1")

    for {
      queue <- InMemoryConsumerProducer[IO, TestRequest1]
      d <- Deferred[IO, TestRequest1]
      _ <- queue.subscribe({ m =>
        println(m)
        for {
          message <- m.message
          r <- d.complete(message)
        } yield r
      })
      _ <- queue.send(request)
      r <- d.get
      _ = println(r)
    } yield assert(r == request)
  }
}
