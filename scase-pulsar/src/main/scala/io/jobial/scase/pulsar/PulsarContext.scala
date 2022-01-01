package io.jobial.scase.pulsar

import org.apache.pulsar.client.api.PulsarClient

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

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
      import scala.concurrent.ExecutionContext.Implicits.global
      Await.result(Future(createClient), 1.minute)
    } else createClient
  
  val persistentScheme = "persistent:"

  val nonPersistentScheme = "non-persistent:"

  def topicInDefaultNamespace(topic: String) =
    if (topic.startsWith(persistentScheme) || topic.startsWith(nonPersistentScheme))
      topic
    else
      s"$persistentScheme//$tenant/$namespace/$topic"

}
