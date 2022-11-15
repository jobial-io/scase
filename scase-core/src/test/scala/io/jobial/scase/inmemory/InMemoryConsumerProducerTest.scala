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

import cats.effect.Async
import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.test.ScaseTestHelper
import io.jobial.scase.core.test.TestRequest1
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.serialization._
import org.scalatest.flatspec.AsyncFlatSpec
import scala.concurrent.duration.DurationInt
//import cats.effect.implicits._

class InMemoryConsumerProducerTest extends AsyncFlatSpec with CatsUtils with Logging with ScaseTestHelper {

  implicitly[Async[IO]]

  "consumer" should "receive message" in {
    val request = TestRequest1("1")

    for {
      consumer <- InMemoryConsumer[IO, TestRequest1]
      producer <- consumer.producer
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

  "multiple consumers" should "all receive the messages" in {
    val requests =
      for {
        i <- 0 until 10
      } yield
        TestRequest1(i.toString)

    for {
      producer <- InMemoryProducer[IO, TestRequest1]
      results <- Ref.of[IO, List[TestRequest1]](List())
      _ <- {
        for {
          i <- 0 until 3
        } yield for {
          consumer <- producer.consumer
          _ <- consumer.subscribe({ m =>
            for {
              m <- m.message
              _ <- debug[IO](m.toString)
              _ <- results.update(m :: _)
            } yield m
          })
        } yield consumer
      }.parSequence
      _ <- requests.map(producer.send(_)).parSequence
      _ <- IO.sleep(1.second)
      r <- results.get
    } yield assert(r.sortBy(_.id) === (requests ++ requests ++ requests).toList.sortBy(_.id))
  }

  "multiple subscriptions" should "should receive messages" in {
    val requests =
      for {
        i <- 0 until 10
      } yield
        TestRequest1(i.toString)

    for {
      producer <- InMemoryProducer[IO, TestRequest1]
      results <- Ref.of[IO, List[TestRequest1]](List())
      consumer <- producer.consumer
      _ <- {
        for {
          i <- 0 until 5
        } yield for {
          _ <- consumer.subscribe({ m =>
            for {
              m <- m.message
              _ <- debug[IO](m.toString)
              _ <- results.update(m :: _)
            } yield m
          })
        } yield consumer
      }.parSequence
      _ <- requests.map(producer.send(_)).parSequence
      _ <- IO.sleep(1.second)
      r <- results.get
    } yield assert(r.sortBy(_.id) === requests.toList.sortBy(_.id))
  }
}
