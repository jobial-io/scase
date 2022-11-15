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

import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.parser
import spray.json._

/**
 * Compatibility layer that derives Spray JsonFormat classes from existing Circe Encoders/Decoders. 
 */
trait CirceSprayJsonSupport {

  implicit def jsonWriterFromCirceEncoder[T: Encoder] = new JsonWriter[T] {
    def write(obj: T): JsValue =
    // This is obviously awfully inefficient, should only be used for migration or backcompat purposes...
      Encoder[T].apply(obj).toString.parseJson
  }

  implicit def jsonReaderFromCirceDecoder[T: Decoder] = new JsonReader[T] {
    def read(json: JsValue): T =
    // This is obviously awfully inefficient, should only be used for migration or backcompat purposes...
      Decoder[T].apply(HCursor.fromJson(parser.parse(json.compactPrint).right.get)).right.get
  }

  implicit def jsonFormatFromCirce[T: Encoder : Decoder] = new JsonFormat[T] {
    override def write(obj: T): JsValue = jsonWriterFromCirceEncoder[T].write(obj)

    override def read(json: JsValue): T = jsonReaderFromCirceDecoder[T].read(json)
  }

}
