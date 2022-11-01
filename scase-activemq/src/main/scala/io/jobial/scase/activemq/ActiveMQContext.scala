package io.jobial.scase.activemq

import org.apache.activemq.ActiveMQConnectionFactory
import javax.jms.Session

case class ActiveMQContext(
  host: String = "localhost",
  port: Int = 61616,
  transacted: Boolean = false,
  acknowledgeMode: Int = Session.AUTO_ACKNOWLEDGE
) {

  lazy val connectionFactory = new ActiveMQConnectionFactory(s"tcp://${host}:${port}")

  lazy val connection = {
    val connection = connectionFactory.createConnection
    connection.start
    connection
  }

  def session =
    connection.createSession(transacted, acknowledgeMode)
}
