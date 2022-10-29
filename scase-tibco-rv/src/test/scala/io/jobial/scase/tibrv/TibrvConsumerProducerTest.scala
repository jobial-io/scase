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
package io.jobial.scase.tibrv

import cats.effect.IO
import cats.effect.concurrent.Deferred
import io.jobial.scase.core.test.ServiceTestSupport
import io.jobial.scase.core.test.TestRequest1
import io.jobial.scase.util.Hash.uuid
import io.jobial.scase.marshalling.tibrv.circe._
import io.circe.generic.auto._

class TibrvConsumerProducerTest extends ServiceTestSupport {
  implicit val pulsarContext = TibrvContext()

  "consumer" should "receive message" in {
    assume(!onMacOS)

    val request = TestRequest1("1")
    val subject = s"TEST.HELLO.${uuid(6)}"

    for {
      consumer <- TibrvConsumer[IO, TestRequest1](Seq(subject))
      producer <- TibrvProducer[IO, TestRequest1](subject)
      d <- Deferred[IO, TestRequest1]
      _ <- consumer.subscribe({ m =>
        println(m)
        for {
          message <- m.message
          r <- d.complete(message)
        } yield r
      })
      _ <- producer.send(request)
      r <- d.get
      _ = println(r)
    } yield assert(r == request)
  }

}
