package io.jobial.scase.inmemory

import io.jobial.scase.core.Queue


/**
  * In-memory queue implementation using a LinkedBlockingQueue.
  */
case class InMemoryQueue[M](
  deliverToAllSubscribers: Boolean = true,
  allowMultipleSubscribers: Boolean = false
)(
  //implicit val executionContext: ExecutionContext
) extends Queue[M] with InMemoryConsumerProducer[M] {
  
  
}
