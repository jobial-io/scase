package io.jobial.scase.tools.bridge

import io.jobial.scase.activemq.ActiveMQContext
import io.jobial.scase.pulsar.PulsarContext
import io.jobial.scase.tibrv.TibrvContext
import io.jobial.sclap.core.ArgumentValueParser
import io.jobial.scase.util._
import scala.util.Try

trait ContextParsers {
  
  def splitValue(value: String) =
    value.split(":", -1).map(v => if (v.isEmpty) None else Some(v))

  implicit val tibrvContextArgumentValueParser = new ArgumentValueParser[TibrvContext]() {
    def parse(value: String) = Try {
      val values = splitValue(value)
      TibrvContext(
        host = values(0).getOrElse(TibrvContext.apply$default$1),
        port = values(1).map(_.toInt).getOrElse(TibrvContext.apply$default$2),
        network = values(2).map(Some(_)).getOrElse(TibrvContext.apply$default$3),
        service = values(3).map(Some(_)).getOrElse(TibrvContext.apply$default$4)
      )
    }.toEither

    def empty = TibrvContext()
  }

  implicit val pulsarContextArgumentValueParser = new ArgumentValueParser[PulsarContext]() {
    def parse(value: String) = Try {
      val values = splitValue(value)
      PulsarContext(
        values(0).getOrElse(PulsarContext.apply$default$1),
        values(1).map(_.toInt).getOrElse(PulsarContext.apply$default$2),
        values(2).getOrElse(PulsarContext.apply$default$3),
        values(3).getOrElse(PulsarContext.apply$default$4),
        PulsarContext.apply$default$5
      )
    }.toEither

    def empty = PulsarContext()
  }

  implicit val activemqContextArgumentValueParser = new ArgumentValueParser[ActiveMQContext]() {
    def parse(value: String) = Try {
      val values = splitValue(value)
      ActiveMQContext(
        values(0).getOrElse(ActiveMQContext.apply$default$1),
        values(1).map(_.toInt).getOrElse(ActiveMQContext.apply$default$2),
        values(2).map(_.toBoolean).getOrElse(ActiveMQContext.apply$default$3),
        values(3).map(_.toInt).getOrElse(ActiveMQContext.apply$default$4)
      )
    }.toEither

    def empty = ActiveMQContext()
  }
}
