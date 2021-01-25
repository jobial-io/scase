package io.jobial.scase.inmemory

import cats.effect.IO
import io.jobial.scase.core.{MessageConsumer, MessageProducer, MessageReceiveResult, MessageSendResult, MessageSubscription}

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future._
import scala.concurrent.{ExecutionContext, Future, Promise}
import io.jobial.scase.core.executionContext
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}


trait InMemoryConsumerProducer[M] extends MessageConsumer[M] with MessageProducer[M] {

  val deliverToAllSubscribers: Boolean

  val allowMultipleSubscribers: Boolean

  /**
   * TODO: currently this implementation propagates failures from the subscriptions to the sender mainly
   *  - to allow SNS topics to not commit failed deliveries. This behaviour should be reviewed. Also,
   *  - the subscribers here are not required to commit. This should also be reviewed.
   */
  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M]): IO[MessageSendResult[M]] = {

    val messageReceiveResult = MessageReceiveResult(message, attributes, { () => IO() }, { () => IO() })

    implicit val cs = IO.contextShift(ExecutionContext.global)

    IO.fromFuture(
      IO(
        for {
          _ <- sequence(for {
            subscription <-
              //        if (deliverToAllSubscribers)
              subscriptions.keys
            //        else {
            //          val size = subscriptions.size
            //          if (size > 0) {
            //            val subscription = subscriptions.keys.drop(Random.nextInt(size)).headOption
            //            subscription.orElse(subscriptions.keys.lastOption).toSeq
            //          } else Seq()
            //        }
          } yield Future {
            subscription(messageReceiveResult)
          })
        } yield MessageSendResult[M]()
      )
    )
  }

  val subscriptions = TrieMap[MessageReceiveResult[M] => _, MessageReceiveResult[M] => _]()

  // TODO: the marshallable is ignored here
  override def subscribe[T](callback: MessageReceiveResult[M] => T)(implicit u: Unmarshaller[M]) = {
    if (subscriptions.size > 0 && !allowMultipleSubscribers)
      throw new IllegalStateException("Trying to subscribe multiple times")

    subscriptions.put(callback, callback)

    implicit val cs = IO.contextShift(ExecutionContext.global)
    
    new MessageSubscription[M] {
      val subscriptionPromise = Promise[Unit]()

      override def subscription =
        IO.fromFuture(IO(subscriptionPromise.future))

      override def cancel =
        IO {
          subscriptionPromise.trySuccess {
            subscriptions.remove(callback)
          }
        }

      override def isCancelled =
        !subscriptions.contains(callback)
    }
  }
}
