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
package io.jobial.scase.pulsar

import cats.effect.Deferred
import cats.effect.IO
import io.circe.generic.auto._
import io.jobial.scase.core.test.ServiceTestSupport
import io.jobial.scase.core.test.TestRequest1
import io.jobial.scase.marshalling.circe._
import io.jobial.scase.util.Hash.uuid

class PulsarConsumerProducerTest extends ServiceTestSupport {
  implicit val pulsarContext = PulsarContext()

  "consumer" should "receive message" in {
    val request = TestRequest1("1")
    val topic = s"hello-test-${uuid(6)}"

    for {
      consumer <- PulsarConsumer[IO, TestRequest1](Left(topic))
      producer <- PulsarProducer[IO, TestRequest1](topic)
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
