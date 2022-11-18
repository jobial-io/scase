package io.jobial.scase.aws.lambda

import io.jobial.condense.CloudformationStack
import io.jobial.condense.StackContext

object TestServiceStack extends CloudformationStack with TestServiceLambdaConfig {

  def template(implicit context: StackContext) =
    lambda[TestServiceLambdaRequestHandler]
}
