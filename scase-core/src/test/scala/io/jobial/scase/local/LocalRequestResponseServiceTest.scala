package io.jobial.scase.local

import cats.effect.IO
import io.jobial.scase.core.{reqRespClientExtension, Request, RequestContext, RequestProcessor, RequestResponseMapping, RequestResponseTestModel, RequestResponseTestSupport, ScaseTestHelper, SendRequestContext, TestRequest, TestRequest1, TestRequest2, TestResponse, TestResponse1}
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
  
//  def requestTagBasedRequestResponseMapping[REQUEST <: Request[RESPONSE], RESPONSE](request: REQUEST with Request[RESPONSE]) =
//    new RequestResponseMapping[REQUEST, RESPONSE] {}
//    
//  implicit val m1 = requestTagBasedRequestResponseMapping(request1)

  // see https://stackoverflow.com/questions/12827316/how-to-implement-typesafe-callback-system-in-scala
  
//  implicit def requestTagBasedRequestResponseMapping[T <: REQUEST with Request[RESPONSE], ] =
//    new RequestResponseMapping[REQUEST, RESPONSE] {}

  "request-response service" should "reply successfully" in {
    for {
      t <- LocalRequestResponseServiceConfiguration[IO, TestRequest[_ <: TestResponse], TestResponse]("hello").serviceAndClient(requestProcessor)
      (service, client) = t
      r <- reqRespClientExtension(client).sendRequest(request1)
      // TODO: do test
    } yield assert(response1 == r)
  }

}
