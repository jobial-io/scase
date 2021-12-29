package io.jobial.scase.example.greeting.pulsar

import io.jobial.scase.example.greeting.GreetingService
import io.jobial.sclap.CommandLineApp

object GreetingPulsarServer extends CommandLineApp with GreetingServicePulsarConfig {

  def run =
    for {
      service <- greetingServiceConfig.service(new GreetingService{})
      s <- service.startService
    } yield s
}
