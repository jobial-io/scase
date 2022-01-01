package io.jobial.scase.example.greeting.zio

import io.jobial.scase.example.greeting.Hello
import zio._
import zio.interop.catz._
import zio.interop.catz.taskEffectInstance
import zio.interop.catz.implicits._

// TODO: add this back when ZIO is upgraded and ExitCode is available...
//object GreetingZIOAppExample extends zio.App with GreetingServiceConfig {
//
//  def run(args: List[String]) = 
//    (for {
//      t <- greetingServiceConfig.serviceAndClient[Task](new GreetingService {})
//      (service, client) = t
//      _ <- service.start
//      helloResponse <- client ? Hello("world")
//    } yield println(helloResponse.sayingHello)).exitCode
//}
