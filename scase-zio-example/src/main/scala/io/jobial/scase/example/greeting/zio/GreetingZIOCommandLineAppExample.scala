package io.jobial.scase.example.greeting.zio

import io.jobial.scase.example.greeting.Hello
import io.jobial.sclap.zio.ZIOCommandLineApp
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.console._

object GreetingZIOCommandLineAppExample extends ZIOCommandLineApp with GreetingServiceConfig {

  def run =
    for {
      t <- greetingServiceConfig.serviceAndClient[Task](new GreetingService {})
      (service, client) = t
      _ <- service.start
      helloResponse <- client ? Hello("world")
      _ <- putStr(helloResponse.sayingHello)
    } yield ()
}
