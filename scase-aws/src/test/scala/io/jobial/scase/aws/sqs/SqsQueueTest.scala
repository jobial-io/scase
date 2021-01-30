package io.jobial.scase.aws.sqs

import java.util.concurrent.ConcurrentLinkedQueue

import io.jobial.scase.aws.util.AwsContext
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.Await.result
import scala.concurrent.duration._
//import cats.implicits._
import java.lang.Thread.sleep
import org.scalatest.flatspec.AsyncFlatSpec
import scala.collection.JavaConversions._
//import util.service.executionContext

class SqsQueueTest extends AsyncFlatSpec with Eventually {

  implicit val awsContext = AwsContext("eu-west-1", sqsExtendedS3BucketName = Some("cloudtemp-sqs"))
  
  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(15, Seconds)), interval = scaled(Span(100, Millis)))

  val testQueue = SqsQueue[JsValue](s"test-queue-${uuid(5)}")

  val message1 = "hello".toJson

  val message2 = "bello".toJson

  val testQueueLarge = SqsQueue[Array[Byte]](s"test-queue-${uuid(5)}")

  "sending to queue" should "succeed" in {
    result(
      for {
        r1 <- testQueue.send(message1)
        r2 <- testQueue.send(message2)
      } yield {
        println(r1)
        println(r2)
      },
      15 seconds
    )
  }

  "receiving from queue" should "be the right order" in {
    val messages = new ConcurrentLinkedQueue[JsValue]

    testQueue.subscribe { result =>
      result.commit()
      println(result.message)
      messages.add(result.message)
    }.subscription

    sleep(3 seconds)

    assert(messages.size === 2)
    println(messages)
    // ordering is not preserved on standard queues!
    assert(messages.toSet === Set(message1, message2))
  }

  "sending a large message to the queue" should "succeed" in {
    val largeMessage = (0 until 300000).map(_.toByte).toArray[Byte]

    result(
      for {
        r <- testQueueLarge.send(largeMessage)(util.marshalling.rawByteArrayMarshallable)
      } yield {
        println(r)
      },
      120 seconds
    )
  }

  "receiving a large message" should "succeed" in {
    val messages = new ConcurrentLinkedQueue[Array[Byte]]

    testQueueLarge.subscribe { result =>
      result.commit()
      messages.add(result.message)
    }(util.marshalling.rawByteArrayMarshallable).subscription

    eventually {
      assert(messages.size === 1)
      println(messages)
    }
  }


}
