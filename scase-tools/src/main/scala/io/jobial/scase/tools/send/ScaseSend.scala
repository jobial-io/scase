package io.jobial.scase.tools.send

import cats.effect.IO
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.rawbytes._
import io.jobial.scase.tools.bridge.EndpointInfo
import io.jobial.scase.tools.bridge.EndpointInfoParser
import io.jobial.sclap.CommandLineApp
import org.apache.commons.io.IOUtils.toByteArray

import java.io.FileInputStream

object ScaseSend extends CommandLineApp with EndpointInfoParser with Logging {

  def run =
    for {
      destination <- opt[EndpointInfo]("destination", "d").required
      file <- opt[String]("file", "f")
    } yield for {
      client <- EndpointInfo.clientForDestination[IO, Array[Byte]](destination)
      message <- readMessage(file)   
      r <- client.send(message)
    } yield r

  def readMessage(file: Option[String]) = IO {
    file match {
      case Some(file) =>
        toByteArray(new FileInputStream(file))
      case None =>
        toByteArray(System.in)
    }
  }

}
