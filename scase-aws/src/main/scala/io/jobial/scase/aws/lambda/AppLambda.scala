package io.jobial.scase.aws.lambda

import cats.effect.IO
import cats.implicits.catsSyntaxFlatMapOps
import io.jobial.scase.core.RequestContext
import io.jobial.scase.core.RequestResponseMapping
import io.jobial.scase.core._
import io.jobial.scase.marshalling.circe._

abstract class AppLambda[C <: {def main(args: Array[String])}](app: C) extends IOLambdaRequestHandler[Array[String], Unit] {

  implicit val mapping = new RequestResponseMapping[Array[String], Unit] {}

  def functionName = getClass.getSimpleName

  override def serviceConfiguration = LambdaServiceConfiguration.requestResponse[Array[String], Unit](functionName)

  override def handleRequest(implicit context: RequestContext[IO]) = {
    case args =>
      delay(app.main(args)) >>
        args.reply(())
  }
}
