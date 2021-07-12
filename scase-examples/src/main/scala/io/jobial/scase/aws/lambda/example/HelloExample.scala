package io.jobial.scase.aws.lambda.example

import cats.effect.IO
import io.jobial.scase.aws.lambda.IOLambdaRequestHandler
import io.jobial.scase.core.{Request, RequestContext, RequestProcessor}
import io.jobial.scase.marshalling.sprayjson._
import spray.json.{DefaultJsonProtocol, JsValue, JsonReader, JsonWriter}

sealed trait HelloExampleRequest[RESPONSE] extends Request[RESPONSE]

sealed trait HelloExampleResponse

case class Hello(message: String) extends HelloExampleRequest[HelloResponse]

case class HelloResponse(responseMessage: String) extends HelloExampleResponse

trait HelloExample extends RequestProcessor[IO, HelloExampleRequest[_], HelloExampleResponse] {

  override def processRequest(implicit context: RequestContext[IO]): Processor = {
    case m: Hello =>
      m.reply(HelloResponse(s"received: ${m.message}"))
  }
}

object HelloExampleLambdaRequestHandler
  extends HelloExample
    with IOLambdaRequestHandler[HelloExampleRequest[_], HelloExampleResponse]
    with DefaultJsonProtocol {

  val helloFormat = jsonFormat1(Hello)

  val helloResponseFormat = jsonFormat1(HelloResponse)

  implicit val r = new JsonReader[HelloExampleRequest[_]] {
    def read(json: JsValue) =
      helloFormat.read(json)
  }

  implicit val w = new JsonWriter[HelloExampleResponse] {
    def write(obj: HelloExampleResponse) = obj match {
      case r: HelloResponse =>
        helloResponseFormat.write(r)
    }
  }

  val requestUnmarshaller = sprayJsonUnmarshaller[HelloExampleRequest[_]]

  val responseMarshaller = sprayJsonMarshaller[HelloExampleResponse]
}

object HelloExampleCloudformationTemplate {
  
}