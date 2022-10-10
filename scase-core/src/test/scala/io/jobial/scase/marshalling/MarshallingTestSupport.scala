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
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import cats.instances.either._
import cats.tests.StrictCatsEquality
import io.jobial.scase.core.{ScaseTestHelper, ServiceTestModel, TestException}
import io.jobial.scase.marshalling.javadsl.Marshalling
import org.apache.commons.io.output.ByteArrayOutputStream
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import java.io.ByteArrayInputStream

trait MarshallingTestSupport extends AsyncFlatSpec
  with StrictCatsEquality
  with ScaseTestHelper
  with ServiceTestModel {

  def testMarshalling[M: Marshaller : Unmarshaller : Eq](message: M, testUnmarshalError: Boolean) = {
    val buf = new ByteArrayOutputStream

    for {
      _ <- Marshaller[M].marshal[IO](message, buf)
      _ = buf.close
      r <- Unmarshaller[M].unmarshal[IO](new ByteArrayInputStream(buf.toByteArray))
    } yield {
      assert(Unmarshaller[M].unmarshal(buf.toByteArray) === message.asRight[Throwable])
      assert(r === message)
      assert(Unmarshaller[M].unmarshal(Marshaller[M].marshal(message)) === message.asRight[Throwable])
      assert(Unmarshaller[M].unmarshalFromText(Marshaller[M].marshalToText(message)) === message.asRight[Throwable])
      if (testUnmarshalError)
        assert(Unmarshaller[M].unmarshal(Array[Byte]()).isLeft)
      else
        succeed
    }
  }

  def testMarshallingWithDefaultFormats[M: Marshaller : Unmarshaller : Eq](message: M, testUnmarshalError: Boolean = false)
    (implicit eitherMarshaller: Marshaller[Either[Throwable, M]], throwableMarshaller: Marshaller[Throwable],
      eitherUnmarshaller: Unmarshaller[Either[Throwable, M]], throwableUnmarshaller: Unmarshaller[Throwable]
    ): IO[Assertion] =
    for {
      r <- testMarshalling(message, testUnmarshalError)
      r <- testMarshalling(new RuntimeException("error"): Throwable, testUnmarshalError)
      r <- testMarshalling(new RuntimeException("error").asLeft[M]: Either[Throwable, M], testUnmarshalError)
    } yield r

  def testJavaMarshalling[M: Eq](message: M, marshalling: Marshalling[M]) = {
    implicit val marshaller = marshalling.marshaller
    implicit val unmarshaller = marshalling.unmarshaller
    implicit val throwableMarshaller = marshalling.throwableMarshaller
    implicit val throwableUnmarshaller = marshalling.throwableUnmarshaller
    implicit val eitherMarshaller = marshalling.eitherMarshaller
    implicit val eitherUnmarshaller = marshalling.eitherUnmarshaller

    testMarshallingWithDefaultFormats(message)
  }

  def testMarshalling[M: Marshaller : Unmarshaller : Eq](message: M, marshalling: Marshalling[M], testUnmarshalError: Boolean = false)
    (implicit eitherMarshaller: Marshaller[Either[Throwable, M]], throwableMarshaller: Marshaller[Throwable],
      eitherUnmarshaller: Unmarshaller[Either[Throwable, M]], throwableUnmarshaller: Unmarshaller[Throwable]
    ) =
    for {
      r1 <- testMarshallingWithDefaultFormats(message)
      r2 <- testJavaMarshalling(message, marshalling)
    } yield List(r1, r2)
}


