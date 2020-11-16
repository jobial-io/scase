package io.jobial.scase.core

import scala.concurrent.Future.successful

class RequestResponseTest {

  val client = new RequestResponseClient[TestRequest, TestResponse]() {
    override def sendRequest[REQUEST <: TestRequest, RESPONSE <: TestResponse](request: REQUEST)
      (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext): RequestResult[RESPONSE] =
      ???
  }

  val processor = new RequestProcessor[TestRequest, TestResponse]() {
    def processRequest(implicit context: RequestContext) = {
      case x: TestRequest1 =>
        // successful(context.send(x, TestResponse1(TestRequest1(""), "hello")))
        // or
        successful(x.reply(TestResponse1(TestRequest1(""), "hello")))
    }
  }
}
