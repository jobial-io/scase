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
package io.jobial.scase.core.impl

import cats.Eq
import cats.effect.IO
import io.jobial.scase.core._
import io.jobial.scase.core.test.ServiceTestSupport
import io.jobial.scase.core.test.TestRequest1
import io.jobial.scase.inmemory.InMemoryConsumer
import io.jobial.scase.inmemory.InMemoryProducer
import io.jobial.scase.marshalling.serialization._

class ForwarderBridgeTest
  extends ServiceTestSupport {

  def testForwarderBridge[REQ: Eq](message: REQ) =
    for {
      source <- InMemoryConsumer[IO, REQ]
      destination <- InMemoryProducer[IO, REQ]
      receiverClient <- ConsumerReceiverClient(source)
      senderClient <- ProducerSenderClient(destination)
      bridge <- ForwarderBridge(receiverClient, senderClient)
      _ <- bridge.start
      sourceProducer <- source.producer
      destinationConsumer <- destination.consumer
      _ <- sourceProducer.send(message)
      r <- destinationConsumer.receive(None)
      receivedMessage <- r.message
      _ <- bridge.stop
      _ <- receiverClient.stop
      _ <- senderClient.stop
    } yield assert(message === receivedMessage)
  
  "forwarder bridge" should "successfully forward" in {
    testForwarderBridge(TestRequest1("hello"))
  }
}

