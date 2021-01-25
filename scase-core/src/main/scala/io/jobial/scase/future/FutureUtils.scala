package io.jobial.scase.future

import java.util.concurrent.{CompletableFuture, ExecutionException, ScheduledThreadPoolExecutor, TimeUnit, TimeoutException}
import java.util.{Timer, TimerTask}
import scala.collection.generic.CanBuildFrom
import scala.concurrent.Future._
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.math.Ordering
import scala.util.{Success, Try}

trait FutureUtils {

  val timer: Timer = new Timer(true)

  /**
   * Returns the result of the provided future within the given time or a timeout exception, whichever is first
   * This uses Java Timer which runs a single thread to handle all futureWithTimeouts and does not block like a
   * Thread.sleep would
   *
   * @param future  Caller passes a future to execute
   * @param timeout Time before we return a Timeout exception instead of future's outcome
   * @return Future[T]
   */
  def futureWithTimeout[T](future: Future[T], timeout: Duration)(implicit ec: ExecutionContext): Future[T] = {

    // Promise will be fulfilled with either the callers Future or the timer task if it times out
    val p = Promise[T]

    // and a Timer task to handle timing out

    val timerTask = new TimerTask() {
      def run(): Unit = {
        p.tryFailure(new TimeoutException())
      }
    }

    // Set the timeout to check in the future
    timer.schedule(timerTask, timeout.toMillis)

    future.map {
      a =>
        if (p.trySuccess(a)) {
          timerTask.cancel()
        }
    }
      .recover {
        case e: Exception =>
          if (p.tryFailure(e)) {
            timerTask.cancel()
          }
      }

    p.future
  }

  def futures[T](f: Future[T]*)(implicit ec: ExecutionContext): Future[Seq[T]] = {
    implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
    //println(s"using execution context $ec, service ec is: " + util.service.executionContext + " global is: " + scala.concurrent.ExecutionContext.Implicits.global)
    sequence(f.toSeq)
  }

  private val scheduledExecutor = new ScheduledThreadPoolExecutor(1)
  
  // Schedule on scheduledExecutor, but run future on the provided execution context
  def scheduledFuture[T](delay: FiniteDuration)(block: => T = {})(implicit executor: ExecutionContext): Future[T] = {
    val promise = Promise[T]

    scheduledExecutor.schedule(new Runnable {
      def run = {
        promise.completeWith(Future(block))
      }
    }, delay.toMillis, TimeUnit.MILLISECONDS)
    promise.future
  }

  def futureFromTry[T](r: => T): Future[T] = Promise.fromTry(Try(r)).future

  def waitForever =
    Promise().future

  implicit def toScalaFuture[T](f: java.util.concurrent.Future[T])(implicit ec: ExecutionContext) = Future {
    // unfortunately we can't do any better here...
    f.get
  } recoverWith {
    case t: ExecutionException =>
      failed(t.getCause)
    case t =>
      failed(t)
  }
}
