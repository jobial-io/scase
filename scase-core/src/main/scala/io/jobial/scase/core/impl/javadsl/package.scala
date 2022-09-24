package io.jobial.scase.core.impl

import scala.collection.JavaConverters._

package object javadsl {

  def javaMapToScala[A, B](map: java.util.Map[A, B]) = map.asScala.toMap
}
