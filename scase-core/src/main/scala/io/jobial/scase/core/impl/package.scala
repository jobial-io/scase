package io.jobial.scase.core

import java.util.concurrent.Executors.newCachedThreadPool
import scala.concurrent.ExecutionContext.fromExecutor

package object impl {

  val blockerContext = fromExecutor(newCachedThreadPool)
}
