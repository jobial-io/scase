package io.jobial.scase.example.greeting.sqs

import io.jobial.scase.example.greeting.sqs.GreetingServiceSqsConfig
import io.jobial.sclap.CommandLineApp

object GreetingSqsServer extends CommandLineApp with GreetingServiceSqsConfig {

  def run =
    for {
      service <- greetingServiceConfig.service(new GreetingService{})
      _ = println("starting service...")
      result <- service.startAndJoin
    } yield result
}
