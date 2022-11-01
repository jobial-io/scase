package io.jobial.scase.tools.bridge

import cats.effect.IO
import io.circe.generic.auto._
import io.jobial.scase.activemq.ActiveMQContext
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.RequestResponseClient
import io.jobial.scase.core.Service
import io.jobial.scase.core.test.ServiceTestSupport
import io.jobial.scase.core.test.TestRequest
import io.jobial.scase.core.test.TestResponse
import io.jobial.scase.marshalling.tibrv.raw.javadsl.TibrvMsgRawMarshalling
import io.jobial.scase.pulsar.PulsarContext
import io.jobial.scase.pulsar.PulsarServiceConfiguration
import io.jobial.scase.tibrv.TibrvContext
import io.jobial.scase.tibrv.TibrvServiceConfiguration
import io.jobial.scase.tools.bridge.ScaseBridge._
import io.jobial.scase.util.Hash.uuid

class ScaseBridgeTest extends ServiceTestSupport {

  implicit val tibrvContext = TibrvContext()
  implicit val pulsarContext = PulsarContext()
  implicit val activemqContext = ActiveMQContext()

  "parsing command line" should "work" in {
    for {
      _ <- pulsarContextArgumentValueParser.parse("localhost::tenant:namespace")
      _ <- tibrvContextArgumentValueParser.parse("localhost::network:1")
    } yield succeed
  }

  def testBridge[M](
    destinationService: RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] => IO[Service[IO]],
    sourceClient: IO[RequestResponseClient[IO, TestRequest[_ <: TestResponse], TestResponse]],
    source: String,
    destination: String,
    bridgeContext: IO[BridgeContext[M]]
  ) = {
    for {
      service <- destinationService(requestHandler)
      client <- sourceClient
      bridgeContext <- bridgeContext
      (requestResponseBridge, forwarderBridge) <- startBridge(source, destination)(bridgeContext)
      r <- testSuccessfulReply(service, client)
      _ <- requestResponseBridge.stop
      _ <- forwarderBridge.stop
    } yield r
  }

  "pulsar to rv" should "work" in {
    assume(!onMacOS)

    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.tibrv.circe._
    val destinationServiceConfig = TibrvServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](Seq(topic))
    val sourceServiceConfig = PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic)

    testBridge(
      destinationServiceConfig.service(_),
      sourceServiceConfig.client,
      s"pulsar://${topic}",
      "tibrv://",
      BridgeContext(Some(tibrvContext), Some(pulsarContext), None, new TibrvMsgRawMarshalling)
    )
  }

  "rv to pulsar" should "work" in {
    assume(!onMacOS)

    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.tibrv.circe._
    val destinationServiceConfig = PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic)
    val sourceServiceConfig = TibrvServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](Seq(topic))

    for {
      service <- destinationServiceConfig.service(requestHandler)
      client <- sourceServiceConfig.client[IO]
      bridgeContext <- BridgeContext(Some(tibrvContext), Some(pulsarContext), None, new TibrvMsgRawMarshalling)
      (requestResponseBridge, forwarderBridge) <- startBridge(s"tibrv://${topic}", "pulsar://")(bridgeContext)
      r <- testSuccessfulReply(service, client)
      _ <- requestResponseBridge.stop
      _ <- forwarderBridge.stop
    } yield r
  }

  "pulsar to jms" should "work" in {
    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.serialization._

    for {
      bridgeContext <- BridgeContext(None, Some(pulsarContext), Some(activemqContext), new javadsl.SerializationMarshalling[Any])
      (requestResponseBridge, forwarderBridge) <- bridgeContext.runBridge(s"pulsar://${topic}", "jms://")
      client <- PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic).client
      r <- testSuccessfulReply(requestResponseBridge.service, client)
      _ <- requestResponseBridge.stop
      _ <- forwarderBridge.stop
    } yield r
  }
}