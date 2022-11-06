package io.jobial.scase.tools.bridge

import cats.effect.IO
import io.circe.generic.auto._
import io.jobial.scase.activemq.ActiveMQContext
import io.jobial.scase.core.ReceiverClient
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.RequestResponseClient
import io.jobial.scase.core.SenderClient
import io.jobial.scase.core.Service
import io.jobial.scase.core.test.ServiceTestSupport
import io.jobial.scase.core.test.TestRequest
import io.jobial.scase.core.test.TestRequest1
import io.jobial.scase.core.test.TestResponse
import io.jobial.scase.jms.JMSServiceConfiguration
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
      _ <- tibrvContextArgumentValueParser.parse("localhost::network::")
      _ <- activemqContextArgumentValueParser.parse("localhost:1:true:1")
    } yield succeed
  }

  "pulsar to rv" should "work" in {
    assume(!onMacOS)

    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.tibrv.circe._
    import io.jobial.scase.marshalling.tibrv.raw.tibrvMsgRawMarshalling

    testBridge(
      TibrvServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](Seq(topic)).service(_),
      PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic).client,
      TibrvServiceConfiguration.source[TestRequest1](Seq(topic)).client,
      PulsarServiceConfiguration.destination[TestRequest1](topic).client,
      s"pulsar://${topic}",
      "tibrv://",
      BridgeContext(tibrvContext = Some(tibrvContext), pulsarContext = Some(pulsarContext))
    )
  }

  "rv to pulsar" should "work" in {
    assume(!onMacOS)

    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.tibrv.circe._
    import io.jobial.scase.marshalling.tibrv.raw.tibrvMsgRawMarshalling

    testBridge(
      PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic).service(_),
      TibrvServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](Seq(topic)).client,
      PulsarServiceConfiguration.source[TestRequest1](topic).client,
      TibrvServiceConfiguration.destination[TestRequest1](topic).client,
      s"tibrv://${topic}",
      "pulsar://",
      BridgeContext(tibrvContext = Some(tibrvContext), pulsarContext = Some(pulsarContext))
    )
  }

  "pulsar to jms" should "work" in {
    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.serialization._
    implicit val session = activemqContext.session

    testBridge(
      JMSServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic, session.createQueue(topic)).service(_),
      PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic).client,
      JMSServiceConfiguration.source[TestRequest1](session.createQueue(topic)).client,
      PulsarServiceConfiguration.destination[TestRequest1](topic).client,
      s"pulsar://${topic}",
      "jms://",
      BridgeContext[Any](pulsarContext = Some(pulsarContext), activemqContext = Some(activemqContext))
    )
  }

//  "jms to pulsar" should "work" in {
//    val topic = s"hello-test-${uuid(6)}"
//    import io.jobial.scase.marshalling.serialization._
//    implicit val session = activemqContext.session
//
//    testBridge(
//      PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic).service(_),
//      JMSServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic, session.createQueue(topic)).client,
//      PulsarServiceConfiguration.source[TestRequest1](topic).client,
//      JMSServiceConfiguration.destination[TestRequest1](session.createQueue(topic)).client,
//      s"jms://${topic}",
//      "pulsar://",
//      BridgeContext[Any](pulsarContext = Some(pulsarContext), activemqContext = Some(activemqContext))
//    )
//  }

  def testBridge[M](
    destinationService: RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] => IO[Service[IO]],
    sourceClient: IO[RequestResponseClient[IO, TestRequest[_ <: TestResponse], TestResponse]],
    destinationReceiverClient: IO[ReceiverClient[IO, TestRequest1]],
    sourceSenderClient: IO[SenderClient[IO, TestRequest1]],
    source: String,
    destination: String,
    bridgeContext: IO[BridgeContext[M]]
  ) =
    for {
      service <- destinationService(requestHandler)
      client <- sourceClient
      bridgeContext <- bridgeContext
      //      sourceSenderClient <- sourceSenderClient
      //      destinationReceiverClient <- destinationReceiverClient
      (requestResponseBridge, forwarderBridge) <- startBridge(source, destination)(bridgeContext)
      r <- testSuccessfulReply(service, client)
      //_ <- testSenderReceiver(sourceSenderClient, destinationReceiverClient, request1)
      _ <- requestResponseBridge.stop
      _ <- forwarderBridge.stop
    } yield r

}