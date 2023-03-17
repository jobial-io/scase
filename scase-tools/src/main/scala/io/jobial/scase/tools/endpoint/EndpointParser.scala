package io.jobial.scase.tools.endpoint

import io.jobial.scase.util.EitherUtils
import io.jobial.sclap.core.ArgumentValueParser
import io.lemonlabs.uri.Uri

trait EndpointParser {

  implicit val endpointInfoArgumentValueParser = new ArgumentValueParser[Endpoint] with EitherUtils() {
    def parse(value: String) =
      Endpoint(Uri.parse(value))

    def empty = Endpoint(Uri.parse("pulsar://")).toOption.get
  }
}
