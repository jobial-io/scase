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

import io.circe.Decoder.Result
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, HCursor, Json}
import cats.syntax.either._
import io.jobial.scase.marshalling.MarshallingUtils

import scala.util.Try

trait DefaultCodecs extends MarshallingUtils {

  implicit val throwableEncoder: Encoder[Throwable] = new Encoder[Throwable] {
    override def apply(a: Throwable): Json = Json.obj(
      "errorMessage" -> Json.fromString(a.getMessage),
      "errorType" -> Json.fromString(a.getClass.getName)
      // TODO: add stackTrace
    )
  }

  implicit val throwableDecoder:Decoder[Throwable] = new Decoder[Throwable] {
    override def apply(c: HCursor): Result[Throwable] = {
      for {
        message <- c.downField("errorMessage").as[String]
        className <- c.downField("errorType").as[String]
      } yield createThrowable(className, message)
    }
  }

  // TODO: revisit this. Added encoder/decoder for Either to prevent auto generation. See https://github.com/circe/circe/issues/751 

  implicit def encodeEither[A, B](implicit
    encoderA: Encoder[A],
    encoderB: Encoder[B]
  ): Encoder[Either[A, B]] = new Encoder[Either[A, B]] {
    override def apply(o: Either[A, B]): Json = o match {
      case Left(a: A) =>
        encoderA(a)
      case Right(b: B) =>
        encoderB(b)
    }
  }

  implicit def decodeEither[A, B](implicit
    decoderA: Decoder[A],
    decoderB: Decoder[B]
  ): Decoder[Either[A, B]] = {
    val left: Decoder[Either[A, B]] = decoderA.map(Left.apply)
    val right: Decoder[Either[A, B]] = decoderB.map(Right.apply)
    // Prioritising right over left
    right or left
  }

}
