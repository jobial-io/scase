package io.jobial.scase.pulsar

import cats.effect.IO
import io.circe.generic.auto._
import io.jobial.scase.core._
import io.jobial.scase.marshalling.circe._
import io.jobial.scase.pulsar.PulsarServiceConfiguration.requestResponse
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

}
