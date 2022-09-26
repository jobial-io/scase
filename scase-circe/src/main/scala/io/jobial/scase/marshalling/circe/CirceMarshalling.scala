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
package io.jobial.scase.marshalling.circe

import io.circe.Decoder
import io.circe.Encoder
import io.circe.parser.decode
import io.circe.syntax._
import io.jobial.scase.marshalling.BinaryFormatMarshaller
import io.jobial.scase.marshalling.BinaryFormatUnmarshaller
import org.apache.commons.io.IOUtils
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8

trait CirceMarshalling {

  implicit def circeMarshaller[T: Encoder] = new BinaryFormatMarshaller[T] {

    def marshalToOutputStream(o: T, out: OutputStream) = {
      val ps = new PrintStream(out, false, UTF_8.name)
      ps.print(o.asJson.toString)
      ps.close
    }

    override def marshalToText(o: T) =
      o.asJson.toString
  }

  implicit def circeUnmarshaller[T: Decoder] = new BinaryFormatUnmarshaller[T] {
    def unmarshalFromInputStream(in: InputStream) =
      unmarshalFromText(IOUtils.toString(in, UTF_8))

    override def unmarshalFromText(text: String) =
      decode[T](text)
  }

}
