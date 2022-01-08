/*
 * Copyright (c) 2020 Jobial OÜ. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package io.jobial.scase.aws.client

import scala.collection.concurrent.TrieMap
import scala.util.hashing.Hashing

package object identitymap {

  def identityTrieMap[K, V] = new TrieMap[K, V](
    Hashing.fromFunction(m => System.identityHashCode(m)),
    // apparently it is ok to cast Any to AnyRef, see https://stackoverflow.com/questions/23892167/convert-scala-any-to-java-object
    Equiv.fromFunction({ (a, b) => Equiv.reference.equiv(a.asInstanceOf[AnyRef], b.asInstanceOf[AnyRef]) })
  )
}
