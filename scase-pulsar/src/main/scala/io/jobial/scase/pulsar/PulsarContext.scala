package io.jobial.scase.pulsar

import cats.effect.IO
import cats.implicits.catsSyntaxFlatMapOps
import org.apache.pulsar.client.api.PulsarClient
import scala.concurrent.ExecutionContext.global

case class PulsarContext(
  host: String = "localhost",
  port: Int = 6650,
  tenant: String = "public",
  namespace: String = "default",
  useDaemonThreadsInClient: Boolean = true
) {
  val brokerUrl = s"pulsar://$host:$port"

  def createClient =
    PulsarClient
      .builder
      .serviceUrl(brokerUrl)
      .build

  lazy val client =
    if (useDaemonThreadsInClient) {
      // Hack to initialize the Pulsar client on a daemon thread so that it creates daemon threads itself...
      implicit val contextShift = IO.contextShift(global)
      (IO(createClient).start >>= (_.join)).unsafeRunSync()
    } else createClient

  val persistentScheme = "persistent:"

  val nonPersistentScheme = "non-persistent:"

  def fullyQualifiedTopicName(topic: String) =
    if (topic.startsWith(persistentScheme) || topic.startsWith(nonPersistentScheme))
      topic
    else
      s"$persistentScheme//$tenant/$namespace/$topic"

}
