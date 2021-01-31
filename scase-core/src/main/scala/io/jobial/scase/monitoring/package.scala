package io.jobial.scase

import java.lang.management.ManagementFactory

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.sys.process._

package object monitoring {

  val dummyPublisher = new MonitoringPublisher {
    override def gauge(name: String, value: Any) = {}

    override def timing(name: String, start: Long) = {}

    override def increment(name: String, count: Int) = {}

    override def decrement(name: String, count: Int) = {}
  }

  def timing[T](name: String)(f: => T)(implicit publisher: MonitoringPublisher) = {
    val start = System.currentTimeMillis
    val r = f
    publisher.timing(name, start)
    r
  }

  def getProcessId = {
    val jvmName = ManagementFactory.getRuntimeMXBean.getName
    val index = jvmName.indexOf('@')
    if (index > 0)
      Some(jvmName.substring(0, index).toLong)
    else
      None
  }

  def getHostName = {
    val jvmName = ManagementFactory.getRuntimeMXBean.getName
    val index = jvmName.indexOf('@')
    if (index > 0)
      Some(jvmName.substring(index + 1))
    else
      None
  }

  def runJStack(repeat: Boolean = false, delay: Duration = 10.seconds)(implicit ec: ExecutionContext) = {
    getProcessId match {
      case Some(pid) =>
        println(s"found pid $pid")
        Future {
          if (repeat)
            Seq("/bin/sh", "-c", s"while true; do jstack $pid ; sleep ${delay.toSeconds} ; done").!
          else
            s"jstack $pid".!
        }
      case _ =>
        println("could not find process pid")
        Future.failed(new IllegalStateException("could not find process pid"))
    }
  }
}
