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

import io.circe.Json
import io.jobial.scase.marshalling.MarshallingUtils
import spray.json._

import scala.util.Try

trait DefaultFormats extends MarshallingUtils {
  implicit def eitherJsonFormat[A: JsonWriter : JsonReader, B: JsonWriter : JsonReader] = new JsonFormat[Either[A, B]] {
    def write(obj: Either[A, B]): JsValue = obj match {
      case Left(a) =>
        a.toJson
      case Right(b) =>
        b.toJson
    }

    def read(json: JsValue): Either[A, B] =
      Try(Right(json.convertTo[B])).getOrElse(Left(json.convertTo[A]))
  }

  implicit def throwableJsonFormat = new JsonFormat[Throwable] {
    def write(obj: Throwable) = JsObject(
      "errorMessage" -> JsString(obj.getMessage),
      "errorType" -> JsString(obj.getClass.getName)
    )

    def read(json: JsValue) = {
      val message = json.asJsObject.fields("errorMessage").asInstanceOf[JsString].value
      val className = json.asJsObject.fields("errorType").asInstanceOf[JsString].value
      createThrowable(className, message)
    }

  }

}
