package io.jobial.scase.tools.bridge

import cats.effect.IO
import io.circe.generic.auto._
import io.jobial.scase.core.test.ServiceTestSupport
import io.jobial.scase.core.test.TestRequest
import io.jobial.scase.core.test.TestResponse
import io.jobial.scase.marshalling.tibrv.circe._
import io.jobial.scase.pulsar.PulsarContext
import io.jobial.scase.pulsar.PulsarServiceConfiguration
import io.jobial.scase.tibrv.TibrvContext
import io.jobial.scase.tibrv.TibrvServiceConfiguration
import io.jobial.scase.tools.bridge.ScaseBridge.BridgeContext
import io.jobial.scase.tools.bridge.ScaseBridge.startBridge
import io.jobial.scase.util.Hash.uuid

class ScaseBridgeTest extends ServiceTestSupport {

  implicit val tibrvContext = TibrvContext()
  implicit val pulsarContext = PulsarContext()

  "pulsar to rv" should "work" in {
    val topic = s"hello-test-${uuid(6)}"
    val destinationServiceConfig = TibrvServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](Seq(topic))
    val sourceServiceConfig = PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic)

    for {
      service <- destinationServiceConfig.service(requestHandler)
      client <- sourceServiceConfig.client[IO]
      bridgeContext <- BridgeContext(Some(tibrvContext), Some(pulsarContext))
      (requestResponseBridge, forwarderBridge) <- startBridge(s"pulsar://${topic}", "tibrv://")(bridgeContext)
      r <- testSuccessfulReply(service, client)
      _ <- requestResponseBridge.stop
      _ <- forwarderBridge.stop
    } yield r
  }

  "rv to pulsar" should "work" in {
    val topic = s"hello-test-${uuid(6)}"
    val destinationServiceConfig = PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic)
    val sourceServiceConfig = TibrvServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](Seq(topic))

    for {
      service <- destinationServiceConfig.service(requestHandler)
      client <- sourceServiceConfig.client[IO]
      bridgeContext <- BridgeContext(Some(tibrvContext), Some(pulsarContext))
      (requestResponseBridge, forwarderBridge) <- startBridge(s"tibrv://${topic}", "pulsar://")(bridgeContext)
      r <- testSuccessfulReply(service, client)
      _ <- requestResponseBridge.stop
      _ <- forwarderBridge.stop
    } yield r
  }
}
