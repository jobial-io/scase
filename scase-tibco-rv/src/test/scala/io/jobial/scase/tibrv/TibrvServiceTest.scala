package io.jobial.scase.tibrv

import cats.effect.IO
import cats.effect.std.Queue
import io.circe.generic.auto._
import io.jobial.scase.core._
import io.jobial.scase.core.test.Req
import io.jobial.scase.core.test.Resp
import io.jobial.scase.core.test.ServiceTestSupport
import io.jobial.scase.core.test.TestMessageHandler
import io.jobial.scase.core.test.TestRequest
import io.jobial.scase.core.test.TestRequest1
import io.jobial.scase.core.test.TestResponse
import io.jobial.scase.core.test.TestResponse1
import io.jobial.scase.marshalling.tibrv.circe._
import io.jobial.scase.tibrv.TibrvServiceConfiguration.destination
import io.jobial.scase.tibrv.TibrvServiceConfiguration.handler
import io.jobial.scase.tibrv.TibrvServiceConfiguration.requestResponse
import io.jobial.scase.tibrv.TibrvServiceConfiguration.source
import io.jobial.scase.tibrv.TibrvServiceConfiguration.stream
import io.jobial.scase.util.Hash.uuid
import scala.language.postfixOps

class TibrvServiceTest
  extends ServiceTestSupport {

  implicit val tibrvContext = TibrvContext()

  "request-response service" should "reply successfully" in {
    assume(!onMacOS)
    val serviceConfig = requestResponse[TestRequest[_ <: TestResponse], TestResponse](Seq(s"hello-test-${uuid(6)}"))

    for {
      service <- serviceConfig.service(requestHandler)
      client <- serviceConfig.client[IO]
      r <- testSuccessfulReply(service, client)
    } yield r
  }

  "another request-response service" should "reply successfully" in {
    assume(!onMacOS)
    val serviceConfig = requestResponse[Req, Resp](Seq(s"another-test-${uuid(6)}"))

    for {
      service <- serviceConfig.service(anotherRequestProcessor)
      client <- serviceConfig.client[IO]
      r <- testAnotherSuccessfulReply(service, client)
    } yield r
  }

  "request" should "time out if service is not started" in {
    assume(!onMacOS)
    val serviceConfig = requestResponse[TestRequest[_ <: TestResponse], TestResponse](Seq(s"hello-timeout-test-${uuid(6)}"))

    for {
      service <- serviceConfig.service(requestHandler)
      client <- serviceConfig.client[IO]
      r <- testRequestResponseTimeout(client)
    } yield r
  }

  "request-response service" should "reply with error" in {
    assume(!onMacOS)
    val serviceConfig = requestResponse[TestRequest[_ <: TestResponse], TestResponse](Seq(s"hello-error-test-${uuid(6)}"))

    for {
      service <- serviceConfig.service(requestHandlerWithError)
      client <- serviceConfig.client[IO]
      r <- testRequestResponseErrorReply(service, client)
    } yield r
  }

  "stream service" should "reply successfully" in {
    assume(!onMacOS)
    val serviceConfig = stream[TestRequest[_ <: TestResponse], TestResponse](
      Seq(s"hello-test-${uuid(6)}"), s"hello-test-response-${uuid(6)}")

    for {
      service <- serviceConfig.service(requestHandler)
      senderClient <- serviceConfig.senderClient[IO]
      receiverClient <- serviceConfig.receiverClient[IO]
      r <- testSuccessfulStreamReply(service, senderClient, receiverClient)
    } yield r
  }

  "stream service with separate error producer" should "reply successfully" in {
    assume(!onMacOS)
    val responseSubject = s"hello-stream-test-response-${uuid(6)}"
    val errorSubject = s"hello-stream-test-error-${uuid(6)}"
    val serviceConfig = stream[TestRequest[_ <: TestResponse], TestResponse](
      Seq(s"hello-test-${uuid(6)}"), responseSubject, errorSubject)

    for {
      service <- serviceConfig.service(requestHandler)
      senderClient <- serviceConfig.senderClient[IO]
      responseReceiverClient <- serviceConfig.responseReceiverClient[IO]
      errorReceiverClient <- serviceConfig.errorReceiverClient[IO]
      r <- testSuccessfulStreamErrorReply(service, senderClient, responseReceiverClient, errorReceiverClient)
    } yield r
  }

  "stream service" should "reply with error" in {
    assume(!onMacOS)
    val responseSubject = s"hello-stream-error-test-response-${uuid(6)}"
    val serviceConfig = stream[TestRequest[_ <: TestResponse], TestResponse](
      Seq(s"hello-error-test-${uuid(6)}"), responseSubject)

    for {
      service <- serviceConfig.service(requestHandlerWithError)
      senderClient <- serviceConfig.senderClient[IO]
      receiverClient <- serviceConfig.receiverClient[IO]
      r <- testStreamErrorReply(service, senderClient, receiverClient)
    } yield r
  }

  "message handler service" should "receive successfully" in {
    assume(!onMacOS)
    val serviceConfig = handler[TestRequest[_ <: TestResponse]](
      Seq(s"hello-test-handler-${uuid(6)}"))

    for {
      receivedMessage <- Queue.bounded[IO, TestRequest[_ <: TestResponse]](1)
      service <- serviceConfig.service(TestMessageHandler(receivedMessage))
      senderClient <- serviceConfig.client[IO]
      r <- testSuccessfulMessageHandlerReceive(service, senderClient, receivedMessage)
    } yield r
  }

  //  "message handler service with subject pattern" should "receive successfully" in {
  //    val requestSubjectPrefix = s"persistent://public/default/hello-test-handler-${uuid(6)}"
  //    val serviceConfig = handler[TestRequest[_ <: TestResponse]](
  //      s"$requestSubjectPrefix-.*".r
  //    )
  //
  //    for {
  //      receivedMessage <- MVar.empty[IO, TestRequest[_ <: TestResponse]]
  //      senderClient <- destination[TestRequest[_ <: TestResponse]](s"$requestSubjectPrefix-a").client[IO]
  //      service <- serviceConfig.service(TestMessageHandler(receivedMessage))
  //      r <- testSuccessfulMessageHandlerReceive(service, senderClient, receivedMessage)
  //      senderClient <- destination[TestRequest[_ <: TestResponse]](s"$requestSubjectPrefix-b").client[IO]
  //      r <- testSuccessfulMessageHandlerReceive(service, senderClient, receivedMessage)
  //    } yield r
  //  }

  "message destination" should "receive successfully" in {
    assume(!onMacOS)
    val subject = s"hello-source-${uuid(6)}"
    val destinationConfig = destination[TestRequest[_ <: TestResponse]](subject)
    val sourceConfig = source[TestRequest[_ <: TestResponse]](Seq(subject))

    for {
      senderClient <- destinationConfig.client[IO]
      receiverClient <- sourceConfig.client[IO]
      r <- testMessageSourceReceive(senderClient, receiverClient)
    } yield r
  }

  "request-response service" should "succeed in load test" in {
    assume(!onMacOS)
    val serviceConfig = requestResponse[TestRequest[_ <: TestResponse], TestResponse](Seq(s"hello-test-${uuid(6)}"))
    for {
      service <- serviceConfig.service(requestHandler)
      client <- serviceConfig.client[IO]
      r <- testMultipleRequests(service, IO.pure(client), i => TestRequest1(i.toString), i => TestResponse1(TestRequest1(i.toString), i.toString))
    } yield r
  }

  "request-response service" should "succeed in load test with different clients" in {
    assume(!onMacOS)
    val serviceConfig = requestResponse[TestRequest[_ <: TestResponse], TestResponse](Seq(s"hello-test-${uuid(6)}"))
    for {
      service <- serviceConfig.service(requestHandler)
      r <- testMultipleRequests(service, serviceConfig.client[IO], i => TestRequest1(i.toString), i => TestResponse1(TestRequest1(i.toString), i.toString))
    } yield r
  }

}
