package io.jobial.scase.core

import java.util.concurrent.Executors.newCachedThreadPool
import java.util.concurrent.ThreadFactory
import scala.concurrent.ExecutionContext.fromExecutor

package object impl {

  object DaemonThreadFactory extends ThreadFactory {
    def newThread(r: Runnable) = {
      val t = new Thread(r)
      t.setDaemon(true)
      t
    }
  }

  val blockerContext = fromExecutor(newCachedThreadPool(DaemonThreadFactory))
}
