package io.jobial.scase.aws.lambda

import io.jobial.scase.core.TestRequest
import io.jobial.scase.core.TestResponse
import io.jobial.scase.marshalling.circe._
import io.circe.generic.auto._

trait TestServiceLambdaConfig {

  val serviceConfiguration =
    LambdaServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse]("ScaseTestService")
}
