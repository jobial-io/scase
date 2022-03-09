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
package io.jobial.scase.marshalling

import cats.Eq
import cats.implicits.catsSyntaxEitherId
import cats.instances.either._
import cats.tests.StrictCatsEquality
import io.jobial.scase.core.{ScaseTestHelper, ServiceTestModel}
import org.apache.commons.io.output.ByteArrayOutputStream
import org.scalatest.flatspec.AsyncFlatSpec

trait MarshallingTestSupport extends AsyncFlatSpec
  with StrictCatsEquality
  with ScaseTestHelper
  with ServiceTestModel {

  def testMarshalling[M: Marshaller : Unmarshaller : Eq](message: M, testUnmarshalError: Boolean = false) = {
    val buf = new ByteArrayOutputStream
    for {
      _ <- Marshaller[M].marshal(message, buf)
    } yield {
      buf.close
      assert(Unmarshaller[M].unmarshal(buf.toByteArray) === message.asRight[Throwable])
      assert(Unmarshaller[M].unmarshal(Marshaller[M].marshal(message)) === message.asRight[Throwable])
      assert(Unmarshaller[M].unmarshalFromText(Marshaller[M].marshalToText(message)) === message.asRight[Throwable])
      if (testUnmarshalError)
        assert(Unmarshaller[M].unmarshal(Array[Byte]()).isLeft)
      else
        succeed
    }
  }
}


