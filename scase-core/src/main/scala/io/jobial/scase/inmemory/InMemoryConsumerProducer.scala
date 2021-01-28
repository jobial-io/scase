package io.jobial.scase.inmemory

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.implicits._
import io.jobial.scase.core.{MessageConsumer, MessageProducer, MessageReceiveResult, MessageSendResult, MessageSubscription}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import scala.concurrent.{ExecutionContext, Promise}


trait InMemoryConsumerProducer[M] extends MessageConsumer[M] with MessageProducer[M] {

  val deliverToAllSubscribers: Boolean

  val allowMultipleSubscribers: Boolean

  implicit val cs = IO.contextShift(ExecutionContext.global)

  def subscriptions: Ref[IO, List[MessageReceiveResult[M] => IO[_]]]


  /**
   * TODO: currently this implementation propagates failures from the subscriptions to the sender mainly
   *  - to allow SNS topics to not commit failed deliveries. This behaviour should be reviewed. Also,
   *  - the subscribers here are not required to commit. This should also be reviewed.
   */
  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M]): IO[MessageSendResult[M]] = {

    val messageReceiveResult = MessageReceiveResult(message, attributes, { () => IO() }, { () => IO() })

    val x = for {
      r <- subscriptions.get
      _ = println(r)
    } yield (for {
      subscription <- r
    } yield {
      println(s"calling subscription on queue with $messageReceiveResult")
      subscription(messageReceiveResult)
    }).sequence

    x.flatten *> IO(MessageSendResult[M]())
    //        if (deliverToAllSubscribers)
    //        else {
    //          val size = subscriptions.size
    //          if (size > 0) {
    //            val subscription = subscriptions.keys.drop(Random.nextInt(size)).headOption
    //            subscription.orElse(subscriptions.keys.lastOption).toSeq
    //          } else Seq()
    //        }
  }

  // TODO: the marshallable is ignored here
  override def subscribe[T](callback: MessageReceiveResult[M] => IO[T])(implicit u: Unmarshaller[M]) = {
    for {
      r <- subscriptions.update(callback :: _)
      _ = println(callback)
    } yield new MessageSubscription[M] {
      val subscriptionPromise = Promise[Unit]()

      override def subscription =
        IO.fromFuture(IO(subscriptionPromise.future))

      override def cancel =
        ???

      //        IO {
      //          subscriptionPromise.trySuccess {
      //            subscriptions.remove(callback)
      //          }
      //        }

      override def isCancelled =
        ???

      //        !subscriptions.contains(callback)
    }



    //    if (subscriptions.size > 0 && !allowMultipleSubscribers)
    //      throw new IllegalStateException("Trying to subscribe multiple times")
    //
    //    subscriptions.put(callback, callback)
    //
    //    implicit val cs = IO.contextShift(ExecutionContext.global)

  }
}
