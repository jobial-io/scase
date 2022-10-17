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
package io.jobial.scase.aws.sqs

import cats.effect.IO
import cats.effect.concurrent.MVar
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.core._
import cats.implicits._
import io.jobial.scase.core.test.ScaseTestHelper
import io.jobial.scase.core.test.TestRequest
import io.jobial.scase.core.test.TestRequest1
import io.jobial.scase.core.test.TestRequest2
import io.jobial.scase.core.test.TestResponse
import io.jobial.scase.marshalling.serialization._
import io.jobial.scase.util.Hash.uuid
import org.scalatest.flatspec.AsyncFlatSpec

class SqsConsumerProducerTest extends AsyncFlatSpec with ScaseTestHelper {

  implicit val awsContext = AwsContext(region = Some("eu-west-1"), sqsExtendedS3BucketName = Some("cloudtemp-sqs"))

  val message1 = TestRequest1("hello")

  val message2 = TestRequest2("bello")

  val queueUrl = s"test-queue-${uuid(5)}"

  val testProducer = SqsProducer[IO, Array[Byte]](queueUrl)
  val testConsumer = SqsConsumer[IO, Array[Byte]](queueUrl)

  //  val testQueue = SqsQueue[IO, TestRequest[_ <: TestResponse]](s"test-queue-${uuid(5)}")


  //  "sending to queue" should "succeed" in {
  //    for {
  //      r1 <- testQueue.send(message1)
  //      r2 <- testQueue.send(message2)
  //    } yield {
  //      println(r1)
  //      println(r2)
  //      succeed
  //    }
  //  }
  //
  //  "receiving from queue" should "be the right order" in {
  //    val messages = new ConcurrentLinkedQueue[TestRequest[_ <: TestResponse]]
  //
  //    testQueue.subscribe { result =>
  //      result.commit()
  //      println(result.message)
  //      messages.add(result.message)
  //    }.subscription
  //
  //    sleep(3 seconds)
  //
  //    assert(messages.size == 2)
  //    println(messages)
  //    // ordering is not preserved on standard queues!
  //    assert(messages.toSet === Set(message1, message2))
  //  }

  val largeMessage = (0 until 300000).map(_.toByte).toArray[Byte]

  "sending a large message to the queue" should "succeed" in {
    for {
      testProducer <- testProducer
      r <- testProducer.send(largeMessage)
      _ <- testProducer.stop
    } yield {
      //println(r)
      succeed
    }
  }

  "receiving a large message" should "succeed" in {
    for {
      messages <- MVar[IO].empty[Array[Byte]]
      testConsumer <- testConsumer
      subscription <- testConsumer.subscribe { result =>
        for {
          _ <- result.commit
          message <- result.message
          _ <- messages.put(message)
        } yield ()
      }
      m <- IO.race(subscription.join,
        messages.take)
      _ <- subscription.cancel
      _ <- testConsumer.stop
    } yield {
      assert(m.right.map(_ sameElements largeMessage).getOrElse(fail))
    }
  }


}
