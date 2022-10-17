package io.jobial.scase.pulsar

import cats.effect.IO
import io.circe.generic.auto._
import io.jobial.scase.core._
import io.jobial.scase.core.test.ServiceTestSupport
import io.jobial.scase.core.test.TestRequest
import io.jobial.scase.core.test.TestResponse
import io.jobial.scase.marshalling.sprayjson.CirceSprayJsonSupport
import io.jobial.scase.marshalling.sprayjson.DefaultFormats
import io.jobial.scase.marshalling.tibrv.sprayjson._
import io.jobial.scase.pulsar.PulsarServiceConfiguration.requestResponse
import io.jobial.scase.util.Hash.uuid


class PulsarWithTibrvMsgTest
  extends ServiceTestSupport with CirceSprayJsonSupport with DefaultFormats {

  implicit val pulsarContext = PulsarContext()

  "request-response service" should "reply successfully" in {
    val serviceConfig = requestResponse[TestRequest[_ <: TestResponse], TestResponse](s"hello-test-${uuid(6)}")

    for {
      service <- serviceConfig.service(requestHandler)
      client <- serviceConfig.client[IO]
      r <- testSuccessfulReply(service, client)
    } yield r
  }

}
