package io.jobial.scase.inmemory


import io.jobial.scase.core.Topic

import scala.concurrent.ExecutionContext 

case class InMemoryTopic[M]() extends Topic[M] with InMemoryConsumerProducer[M] {

  val deliverToAllSubscribers = true

  val allowMultipleSubscribers = true
}
