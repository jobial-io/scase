package io.jobial.scase.aws.client

import scala.collection.concurrent.TrieMap
import scala.util.hashing.Hashing

object IdentityMap {

  def identityTrieMap[K, V] = new TrieMap[K, V](
    Hashing.fromFunction(m => System.identityHashCode(m)),
    // apparently it is ok to cast Any to AnyRef, see https://stackoverflow.com/questions/23892167/convert-scala-any-to-java-object
    Equiv.fromFunction({ (a, b) => Equiv.reference.equiv(a.asInstanceOf[AnyRef], b.asInstanceOf[AnyRef]) })
  )
}
