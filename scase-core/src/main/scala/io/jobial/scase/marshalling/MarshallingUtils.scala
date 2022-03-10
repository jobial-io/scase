package io.jobial.scase.marshalling

import scala.util.Try

trait MarshallingUtils {

  def createThrowable(className: String, message: String) = {
    val c = Class.forName(className)
    Try(c.getConstructor(classOf[String]).newInstance(message).asInstanceOf[Throwable]) orElse
      Try(c.newInstance.asInstanceOf[Throwable]) getOrElse
      new IllegalStateException(message)
  }
}
