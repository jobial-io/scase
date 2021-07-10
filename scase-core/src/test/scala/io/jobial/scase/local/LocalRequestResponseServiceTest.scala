package io.jobial.scase.local

import cats.effect.IO
import io.jobial.scase.core.{RequestContext, RequestProcessor, RequestResponseTestModel, RequestResponseTestSupport, ScaseTestHelper, TestRequest, TestRequest1, TestRequest2, TestResponse, TestResponse1}
import org.scalatest.flatspec.AsyncFlatSpec
import cats.implicits._

class LocalRequestResponseServiceTest
  extends RequestResponseTestSupport {

  val requestProcessor = new RequestProcessor[IO, TestRequest[_ <: TestResponse], TestResponse] {
    override def processRequest(implicit context: RequestContext[IO]): Processor = {
      case r: TestRequest1 =>
        println("replying...")
        r.reply(response1)
    }
  }
  
  "request-response service" should "reply successfully" in {
    for {
      t <- LocalRequestResponseServiceConfiguration[IO, TestRequest[_ <: TestResponse], TestResponse]("hello").serviceAndClient(requestProcessor)
      (service, client) = t
      // TODO: do test
    } yield assert(true)
  }

}
