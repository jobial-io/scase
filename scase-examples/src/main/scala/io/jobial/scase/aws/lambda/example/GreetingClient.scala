package io.jobial.scase.aws.lambda.example

import cats.effect.IO
import io.jobial.sclap.CommandLineApp

object GreetingClient extends CommandLineApp with GreetingServiceConfig {

  def run =
    for {
      client <- greetingServiceConfig.client[IO]
      response <- client ? Hello("world")
    } yield println(response)
}
