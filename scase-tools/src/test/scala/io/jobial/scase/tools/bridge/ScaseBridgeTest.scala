package io.jobial.scase.tools.bridge

import cats.effect.IO
import cats.effect.IO.delay
import cats.effect.IO.pure
import cats.effect.IO.sleep
import cats.kernel.Eq
import io.circe.generic.auto._
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
import scala.concurrent.duration.DurationInt

class ScaseBridgeTest extends ServiceTestSupport {

  implicit val pulsarContextEq = Eq.fromUniversalEquals[PulsarContext]

  implicit val tibrvContextEq = Eq.fromUniversalEquals[TibrvContext]

  implicit def parseEndpointInfo(v: String) = endpointInfoArgumentValueParser.parse(v).toOption.get

  "destination name pattern" should "work" in {
    val pulsarUriPrefix = "pulsar://host:6650/tenant/namespace"
    val tibrvUriPrefix = "tibrv://host:7500/network/service"

    assert(parseEndpointInfo(s"${pulsarUriPrefix}/XXX.DEV.YYY.ZZZ") ==
      substituteDestinationName(
        s"${pulsarUriPrefix}/([A-Z].*)\\.PROD\\.(.*)",
        s"${pulsarUriPrefix}/$$1.DEV.$$2",
        s"${pulsarUriPrefix}/XXX.PROD.YYY.ZZZ")
    )

    assert(parseEndpointInfo(s"${pulsarUriPrefix}/XXX.DEV.XXX.YYY.ZZZ") ==
      substituteDestinationName(
        s"${tibrvUriPrefix}/([A-Z].*)\\.PROD\\.(.*)",
        s"${pulsarUriPrefix}/$$1.DEV.$$2",
        s"${tibrvUriPrefix}/XXX.PROD.XXX.YYY.ZZZ")
    )

    assert(parseEndpointInfo(s"${pulsarUriPrefix}/XXX.DEV.XXX.YYY.ZZZ") ==
      substituteDestinationName(
        s"${tibrvUriPrefix}/([A-Z].*)\\.PROD\\.(.*).>",
        s"${pulsarUriPrefix}/$$1.DEV.$$2",
        s"${tibrvUriPrefix}/XXX.PROD.XXX.YYY.ZZZ")
    )

    assert(parseEndpointInfo(s"${tibrvUriPrefix}/XXX.DEV.XXX.YYY.ZZZ") ==
      substituteDestinationName(
        s"${pulsarUriPrefix}/XXX.*",
        s"${tibrvUriPrefix}/",
        s"${pulsarUriPrefix}/XXX.DEV.XXX.YYY.ZZZ")
    )

    assert(parseEndpointInfo(s"${pulsarUriPrefix}/XXX.DEV.XXX.YYY.ZZZ") ==
      substituteDestinationName(
        s"${tibrvUriPrefix}/.>",
        s"${pulsarUriPrefix}/",
        s"${tibrvUriPrefix}/XXX.DEV.XXX.YYY.ZZZ")
    )

    assert(parseEndpointInfo(s"${tibrvUriPrefix}/XXX.DEV.XXX.YYY.ZZZ") ==
      substituteDestinationName(
        s"${pulsarUriPrefix}/",
        s"${tibrvUriPrefix}/",
        s"${pulsarUriPrefix}/XXX.DEV.XXX.YYY.ZZZ")
    )

    assert(parseEndpointInfo(s"${pulsarUriPrefix}/XXX.DEV.XXX.YYY.ZZZ") ==
      substituteDestinationName(
        s"${tibrvUriPrefix}/",
        s"${pulsarUriPrefix}/",
        s"${tibrvUriPrefix}/XXX.DEV.XXX.YYY.ZZZ")
    )
  }

  "pulsar to pulsar one-way" should "work" in {
    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.tibrv.circe._
    import io.jobial.scase.marshalling.tibrv.raw.tibrvMsgRawMarshalling

    for {
      context <- BridgeContext(s"pulsar://///(${topic})", "pulsar://///$1-destination", true, 300.seconds)
      r <- {
        implicit val pulsarContext = context.destination.asInstanceOf[PulsarEndpointInfo].context
        testOneWayBridge(
          PulsarServiceConfiguration.source[TestRequest1](s"$topic-destination").client,
          PulsarServiceConfiguration.destination[TestRequest1](topic).client,
          pure(context)
        )
      }
    } yield r
  }

  "pulsar to pulsar one-way with different tenant and namespace" should "work" in {
    assume(!onGithub)

    import org.apache.pulsar.client.admin.PulsarAdmin
    val url = "http://localhost:8080"
    val admin = PulsarAdmin.builder.serviceHttpUrl(url)
      .allowTlsInsecureConnection(true).build

    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.tibrv.circe._
    import io.jobial.scase.marshalling.tibrv.raw.tibrvMsgRawMarshalling

    for {
      _ <- (delay(admin.tenants().createTenant("test", admin.tenants().getTenantInfo("public"))) >> sleep(1.second)).attempt
      _ <- (delay(admin.namespaces.createNamespace("test/scase-bridge")) >> sleep(1.second)).attempt
      context <- BridgeContext(s"pulsar://///(${topic})", "pulsar:///test/scase-bridge/$1-destination", true, 300.seconds)
      r <-
        testOneWayBridge(
          {
            implicit val pulsarContext = context.destination.asInstanceOf[PulsarEndpointInfo].context
            PulsarServiceConfiguration.source[TestRequest1](s"$topic-destination").client
          },
          {
            implicit val pulsarContext = context.source.asInstanceOf[PulsarEndpointInfo].context
            PulsarServiceConfiguration.destination[TestRequest1](topic).client
          },
          pure(context)
        )
    } yield r
  }

  "pulsar to pulsar request-response" should "work" in {
    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.tibrv.circe._
    import io.jobial.scase.marshalling.tibrv.raw.tibrvMsgRawMarshalling

    for {
      context <- BridgeContext(s"pulsar://///(${topic})", "pulsar://///$1-destination", false, 300.seconds)
      r <- {
        implicit val pulsarContext = context.destination.asInstanceOf[PulsarEndpointInfo].context
        testRequestResponseBridge(
          PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](s"$topic-destination").service(_),
          PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic).client,
          pure(context)
        )
      }
    } yield r
  }

  "pulsar to pulsar request-response with different tenant and namespace" should "work" in {
    assume(!onGithub)

    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.tibrv.circe._
    import io.jobial.scase.marshalling.tibrv.raw.tibrvMsgRawMarshalling

    for {
      context <- BridgeContext(s"pulsar://///(${topic})", "pulsar:///test/scase-bridge/$1-destination", false, 300.seconds)
      r <- {
        testRequestResponseBridge(
          {
            implicit val pulsarContext = context.destination.asInstanceOf[PulsarEndpointInfo].context
            PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](s"$topic-destination").service(_)
          },
          {
            implicit val pulsarContext = context.source.asInstanceOf[PulsarEndpointInfo].context
            PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic).client
          },
          pure(context)
        )
      }
    } yield r
  }

  "pulsar to rv one-way" should "work" in {
    assume(!onMacOS)

    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.tibrv.circe._
    import io.jobial.scase.marshalling.tibrv.raw.tibrvMsgRawMarshalling

    for {
      context <- BridgeContext(s"pulsar://///${topic}", "tibrv://", true, 300.seconds)
      r <- {
        implicit val pulsarContext = context.source.asInstanceOf[PulsarEndpointInfo].context
        implicit val tibrvContext = context.destination.asInstanceOf[TibrvEndpointInfo].context
        testOneWayBridge(
          TibrvServiceConfiguration.source[TestRequest1](Seq(topic)).client,
          PulsarServiceConfiguration.destination[TestRequest1](topic).client,
          pure(context)
        )
      }
    } yield r
  }

  "pulsar to rv request-response" should "work" in {
    assume(!onMacOS)

    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.tibrv.circe._
    import io.jobial.scase.marshalling.tibrv.raw.tibrvMsgRawMarshalling

    for {
      context <- BridgeContext(s"pulsar://///${topic}", "tibrv://", false, 300.seconds)
      r <- {
        implicit val pulsarContext = context.source.asInstanceOf[PulsarEndpointInfo].context
        implicit val tibrvContext = context.destination.asInstanceOf[TibrvEndpointInfo].context
        testRequestResponseBridge(
          TibrvServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](Seq(topic)).service(_),
          PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic).client,
          pure(context)
        )
      }
    } yield r
  }

  "rv to pulsar" should "work" in {
    assume(!onMacOS)

    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.tibrv.circe._
    import io.jobial.scase.marshalling.tibrv.raw.tibrvMsgRawMarshalling

    for {
      context <- BridgeContext(s"tibrv://///${topic}", "pulsar://", false, 300.seconds)
      r <- {
        implicit val tibrvContext = context.source.asInstanceOf[TibrvEndpointInfo].context
        implicit val pulsarContext = context.destination.asInstanceOf[PulsarEndpointInfo].context
        testRequestResponseBridge(
          PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic).service(_),
          TibrvServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](Seq(topic)).client,
          pure(context)
        )
      }
    } yield r
  }

  "pulsar to activemq one-way" should "work" in {
    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.serialization._

    for {
      context <- BridgeContext[Any](s"pulsar://///${topic}", "activemq://", true, 300.seconds)
      r <- {
        implicit val pulsarContext = context.source.asInstanceOf[PulsarEndpointInfo].context
        implicit val session = context.destination.asInstanceOf[ActiveMQEndpointInfo].context.session

        testOneWayBridge(
          JMSServiceConfiguration.source[TestRequest1](session.createQueue(topic)).client,
          PulsarServiceConfiguration.destination[TestRequest1](topic).client,
          pure(context)
        )
      }
    } yield r
  }

  "activemq to pulsar" should "work" in {
    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.serialization._

    for {
      context <- BridgeContext[Any](s"activemq:///${topic}", "pulsar://", false, 300.seconds)
      r <- {
        implicit val session = context.source.asInstanceOf[ActiveMQEndpointInfo].context.session
        implicit val pulsarContext = context.destination.asInstanceOf[PulsarEndpointInfo].context

        testRequestResponseBridge(
          PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic).service(_),
          JMSServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic, session.createQueue(topic)).client,
          pure(context)
        )
      }
    } yield r
  }


  "pulsar to activemq request-response" should "work" in {
    val topic = s"hello-test-${uuid(6)}"
    import io.jobial.scase.marshalling.serialization._

    for {
      context <- BridgeContext[Any](s"pulsar://///${topic}", "activemq://", false, 300.seconds)
      r <- {
        implicit val pulsarContext = context.source.asInstanceOf[PulsarEndpointInfo].context
        implicit val session = context.destination.asInstanceOf[ActiveMQEndpointInfo].context.session

        testRequestResponseBridge(
          JMSServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic, session.createQueue(topic)).service(_),
          PulsarServiceConfiguration.requestResponse[TestRequest[_ <: TestResponse], TestResponse](topic).client,
          pure(context)
        )
      }
    } yield r
  }


  def testRequestResponseBridge[M](
    destinationService: RequestHandler[IO, TestRequest[_ <: TestResponse], TestResponse] => IO[Service[IO]],
    sourceClient: IO[RequestResponseClient[IO, TestRequest[_ <: TestResponse], TestResponse]],
    //    destinationReceiverClient: IO[ReceiverClient[IO, TestRequest1]],
    //    sourceSenderClient: IO[SenderClient[IO, TestRequest1]],
    bridgeContext: IO[BridgeContext[M]]
  ) =
    for {
      service <- destinationService(requestHandler)
      client <- sourceClient
      bridgeContext <- bridgeContext
      //      sourceSenderClient <- sourceSenderClient
      //      destinationReceiverClient <- destinationReceiverClient
      bridge <- startBridge(bridgeContext)
      r <- testSuccessfulReply(service, client)
      //_ <- testSenderReceiver(sourceSenderClient, destinationReceiverClient, request1)
      _ <- bridge.stop
    } yield r

  def testOneWayBridge[M](
    destinationReceiverClient: IO[ReceiverClient[IO, TestRequest1]],
    sourceSenderClient: IO[SenderClient[IO, TestRequest1]],
    bridgeContext: IO[BridgeContext[M]]
  ) =
    for {
      bridgeContext <- bridgeContext
      sourceSenderClient <- sourceSenderClient
      destinationReceiverClient <- destinationReceiverClient
      bridge <- startBridge(bridgeContext)
      r <- testSenderReceiver(sourceSenderClient, destinationReceiverClient, request1)
      _ <- bridge.stop
    } yield r
}