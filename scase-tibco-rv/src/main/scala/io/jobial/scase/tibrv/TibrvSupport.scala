package io.jobial.scase.tibrv

import com.tibco.tibrv.Tibrv
import com.tibco.tibrv.TibrvRvdTransport

trait TibrvSupport {

  val context: TibrvContext

  def initRv = if (!Tibrv.isValid) Tibrv.open(Tibrv.IMPL_NATIVE)

  val networkWithSemicolon = for {
    network <- context.network
  } yield
    if (network.startsWith(";"))
      network
    else
      s";$network"

  def createTransport =
    new TibrvRvdTransport(context.service.getOrElse(null), networkWithSemicolon.getOrElse(null), s"${context.host}:${context.port}")

}
