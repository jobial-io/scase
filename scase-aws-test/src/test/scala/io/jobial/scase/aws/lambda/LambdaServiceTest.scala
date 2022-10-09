package io.jobial.scase.aws.lambda

import cats.effect.IO
import io.jobial.scase.core.ServiceTestSupport
import io.jobial.scase.core._
import io.jobial.scase.marshalling.circe._
import io.circe.generic.auto._
import io.jobial.scase.marshalling.Marshaller

class LambdaServiceTest extends ServiceTestSupport with TestServiceLambdaConfig {

  "request-response service" should "reply successfully" in {
    
    println(Marshaller[TestRequest[_ <: TestResponse]].marshalToText(request1))
    for {
      client <- serviceConfiguration.client[IO]
      r <- client ? request1
    } yield assert(r === response1)
  }

}
