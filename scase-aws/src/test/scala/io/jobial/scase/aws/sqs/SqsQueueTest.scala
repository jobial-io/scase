package io.jobial.scase.aws.sqs

import java.util.concurrent.ConcurrentLinkedQueue

import cats._
import cats.implicits._
import cats.effect.IO
import cats.effect.concurrent.MVar
import io.jobial.scase.aws.util.AwsContext
import io.jobial.scase.aws.util.Hash.uuid
import io.jobial.scase.core._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.Await.result
import scala.concurrent.duration._
//import cats.implicits._
import java.lang.Thread.sleep
import org.scalatest.flatspec.AsyncFlatSpec
import scala.collection.JavaConversions._
import io.jobial.scase.marshalling.serialization._

class SqsQueueTest extends AsyncFlatSpec with ScaseTestHelper {

  implicit val awsContext = AwsContext("eu-west-1", sqsExtendedS3BucketName = Some("cloudtemp-sqs"))

  val testQueue = SqsQueue[TestRequest[_ <: TestResponse]](s"test-queue-${uuid(5)}")

  val message1 = TestRequest1("hello")

  val message2 = TestRequest2("bello")

  val testQueueLarge = SqsQueue[Array[Byte]](s"test-queue-${uuid(5)}")

  //  "sending to queue" should "succeed" in {
  //    for {
  //      r1 <- testQueue.send(message1)
  //      r2 <- testQueue.send(message2)
  //    } yield {
  ////      println(r1)
  ////      println(r2)
  //      succeed
  //    }
  //  }
  //
  //  "receiving from queue" should "be the right order" in {
  //    val messages = new ConcurrentLinkedQueue[TestRequest]
  //
  //    testQueue.subscribe { result =>
  //      result.commit()
  //      println(result.message)
  //      messages.add(result.message)
  //    }.subscription
  //
  //    sleep(3 seconds)
  //
  //    assert(messages.size === 2)
  //    println(messages)
  //    // ordering is not preserved on standard queues!
  //    assert(messages.toSet === Set(message1, message2))
  //  }

  val largeMessage = (0 until 300000).map(_.toByte).toArray[Byte]

  "sending a large message to the queue" should "succeed" in {
    for {
      r <- testQueueLarge.send(largeMessage)
    } yield {
      //println(r)
      succeed
    }
  }

  "receiving a large message" should "succeed" in {
    for {
      messages <- MVar[IO].empty[Array[Byte]]
      _ <- testQueueLarge.subscribe { result =>
        for {
          _ <- result.commit()
          _ = println("putting message into var")
          _ <- messages.put(result.message)
        } yield ()
      }
      _ = println("taking message")
      m <- messages.take
    } yield
      assert(m.deep == largeMessage.deep)
  }


}
