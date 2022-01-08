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
package io.jobial.scase.marshalling.sprayjson

import cats.effect.IO
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import org.apache.commons.io.IOUtils
import spray.json._
import io.jobial.scase.util.TryExtension
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import scala.util.Try

trait SprayJsonMarshalling {

  implicit def sprayJsonMarshaller[T: JsonWriter] = new Marshaller[T] {
    def marshal(o: T): Array[Byte] = {
      val b = new ByteArrayOutputStream(256)
      marshalToOutputStream(o, b)
      b.close
      b.toByteArray
    }

    def marshal(o: T, out: OutputStream) =
      IO(marshalToOutputStream(o, out))

    private def marshalToOutputStream(o: T, out: OutputStream) = {
      val ps = new PrintStream(out, false, UTF_8.name)
      ps.print(o.toJson.compactPrint)
      ps.close
      ps
    }

    def marshalToText(o: T) =
      o.toJson.compactPrint
  }

  implicit def sprayJsonUnmarshaller[T: JsonReader] = new Unmarshaller[T] {
    def unmarshal(bytes: Array[Byte]) =
      unmarshalFromInputStream(new ByteArrayInputStream(bytes))

    def unmarshal(in: InputStream) =
      IO.fromEither(unmarshalFromInputStream(in))

    private def unmarshalFromInputStream(in: InputStream) =
      unmarshalFromText(IOUtils.toString(in, UTF_8))

    def unmarshalFromText(text: String) =
      Try(text.parseJson.convertTo[T]).toEither
  }
}
