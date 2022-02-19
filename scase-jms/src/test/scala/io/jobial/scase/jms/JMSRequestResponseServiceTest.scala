package io.jobial.scase.jms

import cats.effect.IO
import io.circe.generic.auto._
import io.jobial.scase.core._
import io.jobial.scase.marshalling.circe._
import io.jobial.scase.util.Hash.uuid

import javax.jms.Session


class JMSRequestResponseServiceTest
  extends RequestResponseTestSupport {

  import org.apache.activemq.ActiveMQConnectionFactory

  val connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616")
  val connection = connectionFactory.createConnection
  connection.start
  implicit val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

  "request-response service" should "reply successfully" in {
    val serviceConfig = JMSRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse](
      s"hello-test-${uuid(5)}", session.createQueue(s"hello-test-${uuid(5)}"),
      Some(session.createQueue(s"hello-test-response-${uuid(5)}")),
      None, None)

    for {
      service <- serviceConfig.service(requestHandler)
      client <- serviceConfig.client[IO]
      r <- testSuccessfulReply(service, client)
    } yield r
  }

  "another request-response service" should "reply successfully" in {
    val serviceConfig = JMSRequestResponseServiceConfiguration[Req, Resp](
      s"another-test-${uuid(5)}", session.createQueue(s"another-test-${uuid(5)}"))

    for {
      service <- serviceConfig.service(anotherRequestProcessor)
      client <- serviceConfig.client[IO]
      r <- testAnotherSuccessfulReply(service, client)
    } yield r
  }

  "request" should "time out if service is not started" in {
    val serviceConfig = JMSRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse](
      s"hello-timeout-test-${uuid(5)}", session.createQueue(s"hello-timeout-test-${uuid(5)}"),
      session.createQueue(s"hello-timeout-test-response-${uuid(5)}"))

    for {
      service <- serviceConfig.service(requestHandler)
      client <- serviceConfig.client[IO]
      r <- testTimeout(client)
    } yield r
  }

  "request-response service" should "reply with error" in {
    val serviceConfig = JMSRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse](
      s"hello-error-test-${uuid(5)}", session.createQueue(s"hello-error-test-${uuid(5)}"), session.createQueue(s"hello-error-test-response-${uuid(5)}"))

    for {
      service <- serviceConfig.service(requestHandlerWithError)
      client <- serviceConfig.client[IO]
      r <- testErrorReply(service, client)
    } yield r
  }

}
