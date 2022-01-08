package io.jobial.scase.example.greeting.pulsar

import io.jobial.sclap.CommandLineApp

object GreetingPulsarServer extends CommandLineApp with GreetingServicePulsarConfig {

  def run =
    for {
      service <- greetingServiceConfig.service(new GreetingService{})
      _ = println("starting service...")
      result <- service.startAndJoin
    } yield result
}
