package io.jobial.scase.pulsar

import scala.util.matching.Regex

package object javadsl {
  val defaultPulsarContext = io.jobial.scase.pulsar.PulsarContext()
  
  def toTopicEither(topic: String, isTopicPattern: Boolean): Either[String, Regex] =
    if (isTopicPattern)
      Right(topic.r)
    else
      Left(topic)
}
