package io.jobial.scase.aws.lambda

import cats.effect.unsafe.IORuntime
import io.jobial.condense.CloudformationStack
import io.jobial.condense.StackContext
import scala.concurrent.duration.DurationInt

object TestServiceStack extends CloudformationStack with TestServiceLambdaConfig {

  def template(implicit context: StackContext) =
    lambda[TestServiceLambdaRequestHandler](timeout = 30.seconds)

  val runtime = IORuntime.global
}
