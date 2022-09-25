package io.jobial.scase.pulsar

import cats.effect.IO
import cats.effect.concurrent.Deferred
import io.circe.generic.auto._
import io.jobial.scase.core._
import io.jobial.scase.marshalling.circe._
import io.jobial.scase.pulsar.PulsarServiceConfiguration.destination
import io.jobial.scase.pulsar.PulsarServiceConfiguration.handler
import io.jobial.scase.pulsar.PulsarServiceConfiguration.requestResponse
import io.jobial.scase.pulsar.PulsarServiceConfiguration.source
import io.jobial.scase.pulsar.PulsarServiceConfiguration.stream
import io.jobial.scase.util.Hash.uuid


class PulsarServiceTest
  extends ServiceTestSupport {

  implicit val pulsarContext = PulsarContext()

  "request-response service" should "reply successfully" in {
    val serviceConfig = requestResponse[TestRequest[_ <: TestResponse], TestResponse](s"hello-test-${uuid(6)}")

    for {
      service <- serviceConfig.service(requestHandler)
      client <- serviceConfig.client[IO]
      r <- testSuccessfulReply(service, client)
    } yield r
  }

  "another request-response service" should "reply successfully" in {
    val serviceConfig = requestResponse[Req, Resp](s"another-test-${uuid(6)}")

    for {
      service <- serviceConfig.service(anotherRequestProcessor)
      client <- serviceConfig.client[IO]
      r <- testAnotherSuccessfulReply(service, client)
    } yield r
  }

  "request" should "time out if service is not started" in {
    val serviceConfig = requestResponse[TestRequest[_ <: TestResponse], TestResponse](s"hello-timeout-test-${uuid(6)}")

    for {
      service <- serviceConfig.service(requestHandler)
      client <- serviceConfig.client[IO]
      r <- testRequestResponseTimeout(client)
    } yield r
  }

  "request-response service" should "reply with error" in {
    val serviceConfig = requestResponse[TestRequest[_ <: TestResponse], TestResponse](s"hello-error-test-${uuid(6)}")

    for {
      service <- serviceConfig.service(requestHandlerWithError)
      client <- serviceConfig.client[IO]
      r <- testRequestResponseErrorReply(service, client)
    } yield r
  }

  "stream service" should "reply successfully" in {
    val serviceConfig = stream[TestRequest[_ <: TestResponse], TestResponse](
      s"hello-test-${uuid(5)}", s"hello-test-response-${uuid(5)}")

    for {
      service <- serviceConfig.service(requestHandler)
      senderClient <- serviceConfig.senderClient[IO]
      receiverClient <- serviceConfig.receiverClient[IO]
      r <- testSuccessfulStreamReply(service, senderClient, receiverClient)
    } yield r
  }

  "stream service with separate error producer" should "reply successfully" in {
    val responseTopic = s"hello-test-response-${uuid(5)}"
    val errorTopic = s"hello-test-error-${uuid(5)}"
    val serviceConfig = stream[TestRequest[_ <: TestResponse], TestResponse](
      s"hello-test-${uuid(5)}", responseTopic, errorTopic)

    for {
      service <- serviceConfig.service(requestHandler)
      senderClient <- serviceConfig.senderClient[IO]
      responseReceiverClient <- serviceConfig.responseReceiverClient[IO]
      errorReceiverClient <- serviceConfig.errorReceiverClient[IO]
      r <- testSuccessfulStreamReply(service, senderClient, responseReceiverClient, errorReceiverClient)
    } yield r
  }
  
  "stream service" should "reply with error" in {
    val responseTopic = s"hello-error-test-response-${uuid(5)}"
    val serviceConfig = stream[TestRequest[_ <: TestResponse], TestResponse](
      s"hello-error-test-${uuid(5)}", responseTopic)

    for {
      service <- serviceConfig.service(requestHandlerWithError)
      senderClient <- serviceConfig.senderClient[IO]
      receiverClient <- serviceConfig.receiverClient[IO]
      r <- testStreamErrorReply(service, senderClient, receiverClient)
    } yield r
  }

  "message handler service" should "receive successfully" in {
    val serviceConfig = handler[TestRequest[_ <: TestResponse]](
      s"hello-test-handler-${uuid(5)}")

    for {
      receivedMessage <- Deferred[IO, TestRequest[_ <: TestResponse]]
      service <- serviceConfig.service(TestMessageHandler(receivedMessage))
      senderClient <- serviceConfig.client[IO]
      r <- testSuccessfulMessageHandlerReceive(service, senderClient, receivedMessage)
    } yield r
  }

  "message destination" should "receive successfully" in {
    val topic = s"hello-source-${uuid(5)}"
    val destinationConfig = destination[TestRequest[_ <: TestResponse]](topic)
    val sourceConfig = source[TestRequest[_ <: TestResponse]](topic)

    for {
      senderClient <- destinationConfig.client[IO]
      receiverClient <- sourceConfig.client[IO]
      r <- testMessageSourceReceive(senderClient, receiverClient)
    } yield r
  }
}
