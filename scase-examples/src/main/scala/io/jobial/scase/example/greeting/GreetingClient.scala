package io.jobial.scase.example.greeting

import cats.effect.IO
import io.jobial.sclap.CommandLineApp

object GreetingClient extends CommandLineApp with GreetingServiceConfig {

  def run =
    for {
      client <- greetingServiceConfig.client[IO]
      helloResponse <- client ? Hello("world")
    } yield println(helloResponse.sayingHello)
}
