/*
 * Copyright (c) 2020 Jobial OÜ. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package io.jobial.scase.example.greeting.zio

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
