package io.jobial.scase.marshalling.sprayjson

import spray.json._
import scala.util.Try

trait DefaultFormats {
  implicit def eitherJsonFormat[A: JsonFormat, B: JsonFormat] = new JsonFormat[Either[A, B]] {
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
    override def write(obj: Throwable): JsValue = ???

    override def read(json: JsValue): Throwable = ???
  }

}
