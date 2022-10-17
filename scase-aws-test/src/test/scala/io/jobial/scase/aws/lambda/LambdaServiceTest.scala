package io.jobial.scase.aws.lambda

import cats.effect.IO
import io.circe.generic.auto._
import io.jobial.scase.core._
import io.jobial.scase.core.test.ServiceTestSupport
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.circe._

class LambdaServiceTest extends ServiceTestSupport with TestServiceLambdaConfig {

  "request-response service" should "reply successfully" in {
    for {
      client <- serviceConfiguration.client[IO]
      r <- client ? request1  
    } yield assert(r === response1)
  }

}
