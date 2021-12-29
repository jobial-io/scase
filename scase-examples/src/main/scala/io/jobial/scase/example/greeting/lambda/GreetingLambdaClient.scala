package io.jobial.scase.example.greeting.lambda

import cats.effect.IO
import io.jobial.scase.example.greeting.Hello
import io.jobial.sclap.CommandLineApp

object GreetingLambdaClient extends CommandLineApp with GreetingServiceLambdaConfig {

  def run =
    for {
      client <- greetingServiceConfig.client[IO]
      helloResponse <- client ? Hello("world")
    } yield println(helloResponse.sayingHello)
}
