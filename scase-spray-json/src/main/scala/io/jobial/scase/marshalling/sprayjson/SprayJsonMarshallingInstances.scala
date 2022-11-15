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

import io.jobial.scase.marshalling.BinaryFormatMarshaller
import io.jobial.scase.marshalling.BinaryFormatUnmarshaller
import org.apache.commons.io.IOUtils
import spray.json._
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.Try

trait SprayJsonMarshallingInstances {

  implicit def sprayJsonMarshaller[T: JsonWriter] = new BinaryFormatMarshaller[T] {
    def marshalToOutputStream(o: T, out: OutputStream) = {
      val ps = new PrintStream(out, false, UTF_8.name)
      ps.print(o.toJson.compactPrint)
      ps.close
    }

    override def marshalToText(o: T) =
      o.toJson.compactPrint
  }

  implicit def sprayJsonUnmarshaller[T: JsonReader] = new BinaryFormatUnmarshaller[T] {
    def unmarshalFromInputStream(in: InputStream) =
      unmarshalFromText(IOUtils.toString(in, UTF_8))

    override def unmarshalFromText(text: String) =
      Try(text.parseJson.convertTo[T]).toEither
  }

  implicit def sprayJsonMarshalling[T: JsonFormat] = new SprayJsonMarshalling[T]
}
