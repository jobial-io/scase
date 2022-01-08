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
package io.jobial.scase.example.greeting.sprayjson


import io.circe.generic.auto._
import io.jobial.scase.example.greeting.GreetingResponse
import io.jobial.scase.marshalling.sprayjson._
import io.jobial.scase.pulsar.{PulsarContext, PulsarRequestResponseServiceConfiguration}
import spray.json._

/**
 * For the sake of convenience, the Spray JsonFormats are derived from Circe here, hence the
 * added CirceSprayJsonSupport - in a real application it makes no difference how these formats are implemented.
 * The Marshallers/Umarshallers are derived from whatever Spray JsonFormats are available.
 */
trait GreetingServicePulsarConfig extends DefaultJsonProtocol with CirceSprayJsonSupport {

  implicit val context = PulsarContext()

  val greetingServiceConfig =
    PulsarRequestResponseServiceConfiguration[GreetingRequest[_ <: GreetingResponse], GreetingResponse]("greeting")
}