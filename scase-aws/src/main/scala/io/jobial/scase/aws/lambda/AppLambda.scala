package io.jobial.scase.aws.lambda

import cats.effect.IO
import cats.implicits.catsKernelStdOrderForString
import cats.implicits.catsSyntaxEq
import cats.implicits.catsSyntaxFlatMapOps
import io.circe.Decoder
import io.circe.Json
import io.jobial.scase.aws.lambda.LambdaServiceConfiguration.requestResponse
import io.jobial.scase.core.RequestContext
import io.jobial.scase.core.RequestResponseMapping
import io.jobial.scase.core._
import io.jobial.scase.marshalling.circe._

abstract class AppLambda[C <: {def main(args: Array[String])}](app: C) extends IOLambdaRequestHandler[Array[String], Unit] {

  implicit val mapping = new RequestResponseMapping[Array[String], Unit] {}

  override def handleRequest(implicit context: RequestContext[IO]) = {
    case args =>
      delay(app.main(args)) >>
        args.reply(())
  }

  def functionName = getClass.getSimpleName

  // To decode empty request as an empty arg list
  implicit val argsDecoder = Decoder.decodeArray[String].prepare { cursor =>
    cursor.withFocus(json => if (json.toString() === "\"\"") Json.arr() else json)
  }

  def serviceConfiguration = requestResponse[Array[String], Unit](functionName)
}
