package io.jobial.scase.tibrv

import com.tibco.tibrv.Tibrv
import com.tibco.tibrv.TibrvRvdTransport

trait TibrvSupport {

  val context: TibrvContext
  
  def initRv = if (!Tibrv.isValid) Tibrv.open(Tibrv.IMPL_NATIVE)
  
  def createTransport =
    new TibrvRvdTransport(context.service.getOrElse(null), context.network.getOrElse(null), s"${context.host}:${context.port}")

}
