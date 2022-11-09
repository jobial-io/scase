package io.jobial.scase.tools.bridge

import cats.effect.IO
import com.tibco.tibrv.TibrvMsg
import io.jobial.scase.pulsar.PulsarServiceConfiguration
import io.jobial.sclap.CommandLineApp
import io.jobial.scase.marshalling.tibrv.raw._
import io.jobial.scase.pulsar.PulsarContext

object PulsarSender extends CommandLineApp {
  
  implicit val context = PulsarContext(host = "cbinflx3", tenant = "cbtech", namespace = "orbang")
  
  def run =
    for {
      client <- PulsarServiceConfiguration.destination[TibrvMsg]("_LOCAL.orbang.CB.PORTAL").client[IO]
      m = new TibrvMsg
      _ = m.add("name", "x")
      r <- client.send(m)
    } yield r

}
