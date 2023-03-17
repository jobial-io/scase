package io.jobial.scase.tools.endpoint

import io.jobial.scase.activemq.ActiveMQContext
import io.jobial.scase.core.test.ServiceTestSupport
import io.jobial.scase.pulsar.PulsarContext
import io.jobial.scase.tibrv.TibrvContext
import io.lemonlabs.uri.Uri

import scala.language.postfixOps

class EndpointTest extends ServiceTestSupport {

  "parsing pulsar uri" should "work" in {
    def test(uri: String, context: PulsarContext, topic: String) = {
      Endpoint(Uri.parse(uri)) match {
        case Right(c: PulsarEndpoint) =>
          assert(c.context == context)
          assert(c.topic == topic)
        case Left(t) =>
          fail(t)
        case _ =>
          fail
      }
    }

    test("pulsar://host:5555/tenant/namespace/topic", PulsarContext("host", 5555, "tenant", "namespace"), "topic")
    test("pulsar://host:5555/tenant//topic", PulsarContext("host", 5555, "tenant"), "topic")
    test("pulsar:///tenant/namespace/topic", PulsarContext("localhost", 6650, "tenant", "namespace"), "topic")
    test("pulsar:///tenant//topic", PulsarContext("localhost", 6650, "tenant"), "topic")
    test("pulsar:////namespace/topic", PulsarContext("localhost", 6650, "public", "namespace"), "topic")
    test("pulsar://///topic", PulsarContext(), "topic")
    test("pulsar://///", PulsarContext(), "")
    test("pulsar://", PulsarContext(), "")
  }

  "parsing activemq uri" should "work" in {
    def test(uri: String, context: ActiveMQContext, destination: String) = {
      Endpoint(Uri.parse(uri)) match {
        case Right(c: ActiveMQEndpoint) =>
          assert(c.context == context)
          assert(c.destinationName == destination)
        case Left(t) =>
          fail(t)
        case _ =>
          fail
      }
    }

    test("activemq://host:5555/destination", ActiveMQContext("host", 5555), "destination")
    test("activemq:///destination", ActiveMQContext(), "destination")
    test("activemq://", ActiveMQContext(), "")
  }

  "parsing tibrv uri" should "work" in {
    def test(uri: String, context: TibrvContext, subject: String) = {
      Endpoint(Uri.parse(uri)) match {
        case Right(c: TibrvEndpoint) =>
          assert(c.context == context)
        case Left(t) =>
          fail(t)
        case _ =>
          fail
      }
    }

    test("tibrv://host:5555/network/service/subject", TibrvContext("host", 5555, Some("network"), Some("service")), "subject")
    test("tibrv://host:5555///subject", TibrvContext("host", 5555), "subject")
    test("tibrv://///subject", TibrvContext(), "subject")
    test("tibrv://", TibrvContext(), "")
  }
  
  
}