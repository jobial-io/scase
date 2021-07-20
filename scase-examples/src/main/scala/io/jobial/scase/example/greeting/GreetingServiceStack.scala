package io.jobial.scase.example.greeting

import io.jobial.scase.aws.lambda.IOLambdaRequestHandler
import io.jobial.scase.cloudformation.{CloudformationStackApp, StackContext}

object GreetingServiceStack extends CloudformationStackApp with GreetingServiceConfig {

  object GreetingServiceLambdaRequestHandler
    extends IOLambdaRequestHandler(greetingServiceConfig)
      with GreetingService

  def template(implicit context: StackContext) =
    lambda(GreetingServiceLambdaRequestHandler)

}

