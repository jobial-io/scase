package io.jobial.scase.example.greeting.lambda

import io.jobial.scase.aws.lambda.IOLambdaRequestHandler
import io.jobial.scase.cloudformation.{CloudformationStackApp, StackContext}

object GreetingServiceStack extends CloudformationStackApp with GreetingServiceLambdaConfig {

  object GreetingServiceLambdaRequestHandler
    extends IOLambdaRequestHandler(greetingServiceConfig)
      with GreetingService

  def template(implicit context: StackContext) =
    lambda(GreetingServiceLambdaRequestHandler)

}
