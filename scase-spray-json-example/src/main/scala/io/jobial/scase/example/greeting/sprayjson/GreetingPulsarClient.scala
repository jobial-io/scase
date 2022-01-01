package io.jobial.scase.example.greeting.sprayjson

import cats.effect.IO
import io.jobial.scase.example.greeting.Hello
import io.jobial.sclap.CommandLineApp

object GreetingPulsarClient extends CommandLineApp with GreetingServicePulsarConfig {

  def run =
    for {
      client <- greetingServiceConfig.client[IO]
      helloResponse <- client ? Hello("world")
    } yield println(helloResponse.sayingHello)
}
