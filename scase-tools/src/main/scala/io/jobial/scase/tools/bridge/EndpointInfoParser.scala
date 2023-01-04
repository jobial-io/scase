package io.jobial.scase.tools.bridge

import io.jobial.scase.util.EitherUtil
import io.jobial.sclap.core.ArgumentValueParser
import io.lemonlabs.uri.Uri

trait EndpointInfoParser {

  implicit val endpointInfoArgumentValueParser = new ArgumentValueParser[EndpointInfo] with EitherUtil () {
    def parse(value: String) =
      EndpointInfo(Uri.parse(value))

    def empty = EndpointInfo(Uri.parse("pulsar://")).toOption.get
  }
}
