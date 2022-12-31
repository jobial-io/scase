package io.jobial.scase.tools.bridge

import io.jobial.sclap.core.ArgumentValueParser
import io.lemonlabs.uri.Uri
import scala.util.Try

trait ConnectionInfoParser {

  implicit val connectionInfoArgumentValueParser = new ArgumentValueParser[ConnectionInfo]() {
    def parse(value: String) =
        PulsarConnectionInfo(Uri.parse(value))
          .orElse(TibrvConnectionInfo(Uri.parse(value)))
          .orElse(ActiveMQConnectionInfo(Uri.parse(value)))

    def empty = new ConnectionInfo {
      override def uri: Uri = ???

      override def canonicalUri: Uri = ???

      override def pathLen: Int = ???
    }
  }
}
