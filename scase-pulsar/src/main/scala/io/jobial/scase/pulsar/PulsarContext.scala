package io.jobial.scase.pulsar

import org.apache.pulsar.client.api.PulsarClient

case class PulsarContext(
  host: String = "localhost",
  port: Int = 6650,
  tenant: String = "public",
  namespace: String = "default"
) {

  val brokerUrl = s"pulsar://$host:$port"

  val client =
    PulsarClient
      .builder
      .serviceUrl(brokerUrl)
      .build

  val persistentScheme = "persistent:"

  val nonPersistentScheme = "non-persistent:"

  def topicInDefaultNamespace(topic: String) =
    if (topic.startsWith(persistentScheme) || topic.startsWith(nonPersistentScheme))
      topic
    else
      s"$persistentScheme//$tenant/$namespace/$topic"
}
