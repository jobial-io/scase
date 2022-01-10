package io.jobial.scase.pulsar

import cats.effect.IO
import io.circe.generic.auto._
import io.jobial.scase.core._
import io.jobial.scase.marshalling.circe._

import scala.concurrent.TimeoutException
import scala.concurrent.duration._


class PulsarRequestResponseServiceTest
  extends RequestResponseTestSupport {

  implicit val pulsarContext = PulsarContext()

  "request-response service" should "reply successfully" in {
    val serviceConfig = PulsarRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse]("hello-test")

    for {
      service <- serviceConfig.service(requestHandler)
      client <- serviceConfig.client[IO]
      r <- testSuccessfulReply(service, client)
    } yield r
  }

  "another request-response service" should "reply successfully" in {
    val serviceConfig = PulsarRequestResponseServiceConfiguration[Req, Resp]("another-test")

    for {
      service <- serviceConfig.service(anotherRequestProcessor)
      client <- serviceConfig.client[IO]
      r <- testAnotherSuccessfulReply(service, client)
    } yield r
  }

  "request" should "time out if service is not started" in {
    val serviceConfig = PulsarRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse]("hello-timeout-test")

    for {
      service <- serviceConfig.service(requestHandler)
      client <- serviceConfig.client[IO]
      r <- testTimeout(client)
    } yield r
  }

  "request-response service" should "reply with error" in {
    val serviceConfig = PulsarRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse]("hello-error-test")

    for {
      service <- serviceConfig.service(requestHandlerWithError)
      client <- serviceConfig.client[IO]
      r <- testErrorReply(service, client)
    } yield r
  }

}
