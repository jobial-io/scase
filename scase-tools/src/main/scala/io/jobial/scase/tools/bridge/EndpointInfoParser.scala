package io.jobial.scase.tools.bridge

import io.jobial.sclap.core.ArgumentValueParser
import io.lemonlabs.uri.Uri
import scala.util.Try

trait EndpointInfoParser {

  implicit val endpointInfoArgumentValueParser = new ArgumentValueParser[EndpointInfo]() {
    def parse(value: String) =
        EndpointInfo(Uri.parse(value))

    def empty = EndpointInfo(Uri.parse("http://")).toOption.get
  }
}
