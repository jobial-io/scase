package io.jobial.scase.pulsar.client

import org.apache.pulsar.client.api.PulsarClient

case class PulsarContext(
  host: String,
  port: Int,
  tenant: String,
  namespace: String
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