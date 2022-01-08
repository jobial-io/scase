package io.jobial.scase.aws.sqs

import cats.effect.IO
import cats.effect.concurrent.MVar
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.aws.client.Hash.uuid
import io.jobial.scase.core.{TestRequest, TestResponse, _}
import cats.implicits._
import io.jobial.scase.marshalling.serialization._
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
          _ <- result.commit()
          _ <- messages.put(result.message)
        } yield ()
      }
      m <- IO.race(subscription.join,
        messages.take)
    } yield {
      assert(m.right.map(_ sameElements largeMessage).getOrElse(fail))
    }
  }


}