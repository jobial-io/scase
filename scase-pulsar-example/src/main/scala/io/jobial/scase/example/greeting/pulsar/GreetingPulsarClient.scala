package io.jobial.scase.example.greeting.pulsar

import cats.effect.IO
import io.jobial.sclap.CommandLineApp

object GreetingPulsarClient extends CommandLineApp with GreetingServicePulsarConfig {

  def run =
    for {
      client <- greetingServiceConfig.client[IO]
      helloResponse <- client ? Hello("world")
    } yield println(helloResponse.sayingHello)
}
