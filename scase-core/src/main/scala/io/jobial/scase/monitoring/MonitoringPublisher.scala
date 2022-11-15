package io.jobial.scase.monitoring


case class Timing(name: String, start: Long, end: Long) {

  def duration = end - start
}

case class Gauge(name: String, time: Long, value: Any)

case class Counter(name: String, time: Long, value: Int)

trait MonitoringPublisher {

  def gauge(name: String, value: Any)

  def timing(name: String, start: Long)

  def increment(name: String, count: Int = 1)

  def decrement(name: String, count: Int = 1)
}
