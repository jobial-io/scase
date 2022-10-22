package io.jobial.scase.tibrv

case class TibrvContext(
  host: String = "localhost",
  port: Int = 7500,
  network: Option[String] = None,
  service: Option[String] = None
)
