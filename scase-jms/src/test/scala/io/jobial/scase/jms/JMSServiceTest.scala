package io.jobial.scase.jms

import cats.effect.IO
import cats.effect.concurrent.Deferred
import io.circe.generic.auto._
import io.jobial.scase.core._
import io.jobial.scase.marshalling.circe._
import io.jobial.scase.util.Hash.uuid

import javax.jms.Session


class JMSServiceTest
  extends ServiceTestSupport {

  import org.apache.activemq.ActiveMQConnectionFactory

  val connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616")
  val connection = connectionFactory.createConnection
  connection.start
  implicit val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

  "request-response service" should "reply successfully" in {
    val serviceConfig = JMSServiceConfiguration.requestResponse[Req, Resp](
      s"another-test-${uuid(5)}", session.createQueue(s"another-test-${uuid(5)}"))

    for {
      service <- serviceConfig.service(anotherRequestProcessor)
      client <- serviceConfig.client[IO]
      r <- testAnotherSuccessfulReply(service, client)
    } yield r
  }

  "request" should "time out if service is not started" in {
    val responseDestination = session.createQueue(s"hello-timeout-test-response-${uuid(5)}")
    val serviceConfig = JMSServiceConfiguration.stream[TestRequest[_ <: TestResponse], TestResponse](
      s"hello-timeout-test-${uuid(5)}", session.createQueue(s"hello-timeout-test-${uuid(5)}"),
      responseDestination)
    val sourceConfig = JMSServiceConfiguration.source[TestResponse](responseDestination)

    for {
      service <- serviceConfig.service(requestHandler)
      senderClient <- serviceConfig.client[IO]
      receiverClient <- sourceConfig.client[IO]
      r <- testStreamTimeout(senderClient, receiverClient)
    } yield r
  }

  "stream service" should "reply successfully" in {
    val responseDestination = session.createQueue(s"hello-test-response-${uuid(5)}")
    val serviceConfig = JMSServiceConfiguration.stream[TestRequest[_ <: TestResponse], TestResponse](
      s"hello-test-${uuid(5)}", session.createQueue(s"hello-test-${uuid(5)}"),
      responseDestination)
    val sourceConfig = JMSServiceConfiguration.source[Either[Throwable, TestResponse]](responseDestination)

    for {
      service <- serviceConfig.service(requestHandler)
      senderClient <- serviceConfig.client[IO]
      receiverClient <- sourceConfig.client[IO]
      r <- testSuccessfulStreamReply(service, senderClient, receiverClient)
    } yield r
  }

  "stream service" should "reply with error" in {
    val responseDestination = session.createQueue(s"hello-error-test-response-${uuid(5)}")
    val serviceConfig = JMSServiceConfiguration.stream[TestRequest[_ <: TestResponse], TestResponse](
      s"hello-error-test-${uuid(5)}", session.createQueue(s"hello-error-test-${uuid(5)}"), responseDestination)
    val sourceConfig = JMSServiceConfiguration.source[Either[Throwable, TestResponse]](responseDestination)

    for {
      service <- serviceConfig.service(requestHandlerWithError)
      senderClient <- serviceConfig.client[IO]
      receiverClient <- sourceConfig.client[IO]
      r <- testStreamErrorReply(service, senderClient, receiverClient)
    } yield r
  }

  "message handler service" should "receive successfully" in {
    val serviceConfig = JMSServiceConfiguration.handler[TestRequest[_ <: TestResponse]](
      s"hello-test-handler-${uuid(5)}", session.createQueue(s"hello-test-handler-${uuid(5)}"))

    for {
      receivedMessage <- Deferred[IO, TestRequest[_ <: TestResponse]]
      service <- serviceConfig.service(TestMessageHandler(receivedMessage))
      senderClient <- serviceConfig.client[IO]
      r <- testSuccessfulMessageHandlerReceive(service, senderClient, receivedMessage)
    } yield r
  }

  "message receiver" should "receive successfully" in {
    val queueName = s"hello-source-${uuid(5)}"
    val destinationConfig = JMSServiceConfiguration.destination[TestRequest[_ <: TestResponse]](
      session.createQueue(queueName))
    val sourceConfig = JMSServiceConfiguration.source[TestRequest[_ <: TestResponse]](
      session.createQueue(queueName))

    for {
      senderClient <- destinationConfig.client[IO]
      receiverClient <- sourceConfig.client[IO]
      r <- testMessageSourceReceive(senderClient, receiverClient)
    } yield r
  }
}
