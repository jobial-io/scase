package io.jobial.scase.marshalling.tibrv.circe

import com.tibco.tibrv.TibrvMsg
import com.tibco.tibrv.TibrvMsgField
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.EncoderOps
import io.jobial.scase.marshalling.BinaryFormatMarshaller
import io.jobial.scase.marshalling.BinaryFormatUnmarshaller
import org.apache.commons.io.IOUtils.toByteArray
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import java.io.InputStream
import java.io.OutputStream
import java.util.Date

/**
 * A marshalling implementation for case classes and TibrvMsg piggybacking on Circe for the case class
 * conversion. It spares the complexity of dealing with case class creation. The flip side is the inherently 
 * lossy encoding of JSON.
 */
trait TibrvMsgCirceMarshallingInstances {

  implicit val intEncoder = new Encoder[Int] {
    def apply(a: Int) = Json.obj(
      "value" -> Json.fromInt(a),
      "$type" -> "int".asJson
    )
  }

  implicit val longEncoder = new Encoder[Long] {
    def apply(a: Long) = Json.obj(
      "value" -> Json.fromLong(a),
      "$type" -> "long".asJson
    )
  }

  implicit val doubleEncoder = new Encoder[Double] {
    def apply(a: Double) = Json.obj(
      "value" -> Json.fromDouble(a).get,
      "$type" -> "double".asJson
    )
  }

  val localDateClassName = classOf[LocalDate].getName

  val dateTimeClassName = classOf[DateTime].getName

  val localDateTimeClassName = classOf[LocalDateTime].getName

  implicit val localDateEncoder = new Encoder[LocalDate] {
    def apply(a: LocalDate) = Json.obj(
      "value" -> Json.fromLong(a.toDate.getTime),
      "$type" -> localDateClassName.asJson
    )
  }

  implicit val dateTimeEncoder = new Encoder[DateTime] {
    def apply(a: DateTime) = Json.obj(
      "value" -> Json.fromLong(a.toDate.getTime),
      "$type" -> dateTimeClassName.asJson
    )
  }

  implicit val localDateTimeEncoder = new Encoder[LocalDateTime] {
    def apply(a: LocalDateTime) = Json.obj(
      "value" -> Json.fromLong(a.toDate.getTime),
      "$type" -> localDateTimeClassName.asJson
    )
  }

  implicit val localDateDecoder = Decoder[Long].map(new LocalDate(_))

  implicit val dateTimeDecoder = Decoder[Long].map(new DateTime(_))

  implicit val localDateTimeDecoder = Decoder[Long].map(new LocalDateTime(_))

  implicit def tibrvMsgCirceMarshaller[M: Encoder]: BinaryFormatMarshaller[M] = new BinaryFormatMarshaller[M] {
    def marshalToOutputStream(o: M, out: OutputStream) = {

      def jsValueToTibrvMsgField(v: Json, m: TibrvMsg, field: String): Unit = {
        // JsNumber is intentionally left out here to fail fast if the wrong non-typed JS formats are used
        // Can't believe there is no better way in circe to do this, anyway...
        if (v.isString)
          m.add(field, v.asString.get)
        else if (v.isBoolean)
          m.add(field, v.asBoolean.get)
        else if (v.isArray)
          m.add(field, jsArrayToTibrvMsg(v))
        else if (v.isObject) {
          v.asObject.get("$type").map(_.asString) match {
            case Some(t) =>
              val value = v.asObject.get("value")
              t match {
                case Some("double") =>
                  m.add(field, value.get.asNumber.get.toDouble)
                case Some("int") =>
                  m.add(field, value.get.asNumber.get.toInt.get)
                case Some("long") =>
                  m.add(field, value.get.asNumber.get.toLong.get)
                case Some(`localDateClassName`) =>
                  m.add(field, new Date(value.get.asNumber.get.toLong.get))
                case Some(`dateTimeClassName`) =>
                  m.add(field, new Date(value.get.asNumber.get.toLong.get))
                case _ =>
                  throw new IllegalStateException(s"unknown type: $t")
              }
            case None =>
              m.add(field, jsObjectToTibrvMsg(v))
          }
        }
      }


      def jsObjectToTibrvMsg(o: Json) = {
        val m = new TibrvMsg

        for {
          f <- o.asObject.get.toVector
        } yield
          jsValueToTibrvMsgField(f._2, m, f._1)

        m
      }

      def jsArrayToTibrvMsg(o: Json) = {
        val m = new TibrvMsg

        // Need to add some info to be able to distinguish empty arrays and objects. Unfortunately, there is no
        // standard here.
        m.add("$type", "JsArray")

        for {
          e <- o.asArray.get
        } yield
          jsValueToTibrvMsgField(e, m, null)

        m
      }

      out.write(jsObjectToTibrvMsg(o.asJson).getAsBytes)
    }

  }

  implicit def tibrvMsgCirceUnmarshaller[M: Decoder]: BinaryFormatUnmarshaller[M] = new BinaryFormatUnmarshaller[M] {
    def unmarshalFromInputStream(in: InputStream): Either[Throwable, M] = {
      val m = new TibrvMsg(toByteArray(in))

      def tibrvMsgToJsValue(m: TibrvMsg): Json =
      // If the message has no fields or the first field has no name, the assumption is that it was 
      // marshalled from a JsArray (fields are indexed rather than named), otherwise assume it was a JsObject
        if (m.getNumFields > 0 && m.getFieldByIndex(0).name == "$type" && m.getFieldByIndex(0).data == "JsArray")
          tibrvMsgToJsArray(m)
        else
          tibrvMsgToJsObject(m)

      def tibrvMsgFieldToJsValue(f: TibrvMsgField) =
        f.data match {
          case s: String =>
            s.asJson
          case i: Integer =>
            i.asJson
          case b: java.lang.Boolean =>
            b.asJson
          case i: java.lang.Long =>
            i.asJson
          case i: java.lang.Double =>
            i.asJson
          case d: Date =>
            Json.fromLong(d.getTime)
          case m: TibrvMsg =>
            tibrvMsgToJsValue(m)
        }

      def tibrvMsgToJsArray(m: TibrvMsg) = {
        for {
          i <- (1 until m.getNumFields).toVector
        } yield tibrvMsgFieldToJsValue(m.getFieldByIndex(i))
      }.asJson

      def tibrvMsgToJsObject(m: TibrvMsg) = {
        (for {
          i <- 0 until m.getNumFields
          f = m.getFieldByIndex(i)
        } yield {
          f.name -> tibrvMsgFieldToJsValue(f)
        }).toMap
      }.asJson

      tibrvMsgToJsValue(m).as[M]
    }
  }
}
