package io.jobial.scase.core

import java.util.concurrent.ExecutorService

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor

case class ExecutionContextWithShutdown(executor: ExecutorService) extends ExecutionContextExecutor {
  val executionContext = ExecutionContext.fromExecutor(executor)

  override def reportFailure(cause: Throwable) =
    executionContext.reportFailure(cause)

  override def execute(command: Runnable) =
    executionContext.execute(command)

  override def prepare():ExecutionContext =
    executionContext.prepare

  def shutdown = executor.shutdown
}
