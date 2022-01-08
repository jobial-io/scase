package io.jobial.scase.example.greeting.sqs

import cats.effect.IO
import io.jobial.sclap.CommandLineApp

object GreetingSqsClient extends CommandLineApp with GreetingServiceSqsConfig {

  def run =
    for {
      client <- greetingServiceConfig.client[IO]
      helloResponse <- client ? Hello("world")
    } yield println(helloResponse.sayingHello)
}
