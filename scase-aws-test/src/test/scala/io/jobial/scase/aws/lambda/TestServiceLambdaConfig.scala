package io.jobial.scase.aws.lambda

import io.jobial.scase.marshalling.circe._
import io.circe.generic.auto._
import io.jobial.scase.core.test.TestRequest
import io.jobial.scase.core.test.TestResponse

trait TestServiceLambdaConfig {

  val serviceConfiguration =
    LambdaServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse]("ScaseTestService")
}
