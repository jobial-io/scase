package io.jobial.scase.local

import cats.effect.IO
import io.jobial.scase.core.{requestTagBasedRequestResponseMapping, RequestContext, RequestProcessor, RequestResponseTestModel, RequestResponseTestSupport, ScaseTestHelper, SendRequestContext, TestRequest, TestRequest1, TestRequest2, TestResponse, TestResponse1}
import org.scalatest.flatspec.AsyncFlatSpec
import cats.implicits._
import scala.concurrent.duration.DurationInt

class LocalRequestResponseServiceTest
  extends RequestResponseTestSupport {

  val config = LocalRequestResponseServiceConfiguration[IO, TestRequest[_ <: TestResponse], TestResponse]("hello")
  
  val requestProcessor = new RequestProcessor[IO, TestRequest[_ <: TestResponse], TestResponse] {
    override def processRequest(implicit context: RequestContext[IO]): Processor = {
      case r: TestRequest1 =>
        println("replying...")
        r.reply(response1)
    }
  }

  implicit val sendRequestContext = SendRequestContext(10.seconds)
  
  "request-response service" should "reply successfully" in {
    for {
      t <- LocalRequestResponseServiceConfiguration[IO, TestRequest[_ <: TestResponse], TestResponse]("hello").serviceAndClient(requestProcessor)
      (service, client) = t
      r <- client.sendRequest(request1)
      // TODO: do test
    } yield assert(response1 == r)
  }

}
