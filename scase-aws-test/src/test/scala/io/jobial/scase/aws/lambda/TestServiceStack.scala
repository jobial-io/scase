package io.jobial.scase.aws.lambda

import cats.effect.unsafe.IORuntime
import io.jobial.condense.CloudformationStack
import io.jobial.condense.StackContext

object TestServiceStack extends CloudformationStack with TestServiceLambdaConfig {

  def template(implicit context: StackContext) =
    lambda[TestServiceLambdaRequestHandler]

  val runtime = IORuntime.global
}
