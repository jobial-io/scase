package io.jobial.scase.core

import java.lang.Integer.MAX_VALUE
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit.SECONDS
import scala.concurrent.ExecutionContext.fromExecutor

package object impl {

  object DaemonThreadFactory extends ThreadFactory {
    def newThread(r: Runnable) = {
      val t = new Thread(r)
      t.setDaemon(true)
      val idx = t.getName.lastIndexOf('-')
      val name = if (idx < 0) t.getName else s"blocker-context-${t.getName.substring(idx + 1)}" 
      t.setName(name)
      t
    }
  }

  val blockerContext = fromExecutor(
    new ThreadPoolExecutor(
      0, MAX_VALUE,
      300L, SECONDS,
      new SynchronousQueue[Runnable], DaemonThreadFactory)
  )
}
