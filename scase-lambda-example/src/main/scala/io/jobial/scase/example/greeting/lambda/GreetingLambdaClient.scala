package io.jobial.scase.example.greeting.lambda

import cats.effect.IO
import io.jobial.sclap.CommandLineApp

import scala.concurrent.ExecutionContext.Implicits.global

object GreetingLambdaClient extends CommandLineApp with GreetingServiceLambdaConfig {

  def run =
    for {
      client <- greetingServiceConfig.client[IO]
      helloResponse <- client ? Hello("world")
    } yield println(helloResponse.sayingHello)
}
