package io.jobial.scase.marshalling.tibrv.sprayjson

import com.tibco.tibrv.TibrvMsg
import com.tibco.tibrv.TibrvMsgField
import io.jobial.scase.marshalling.BinaryFormatMarshaller
import io.jobial.scase.marshalling.BinaryFormatUnmarshaller
import io.jobial.scase.marshalling.sprayjson.DefaultFormats
import io.jobial.scase.util.TryExtensionUtil
import org.apache.commons.io.IOUtils.toByteArray
import org.joda.time.DateTime
import org.joda.time.LocalDate
import spray.json.AdditionalFormats
import spray.json.CollectionFormats
import spray.json.JsArray
import spray.json.JsBoolean
import spray.json.JsNumber
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.JsonReader
import spray.json.JsonWriter
import spray.json.ProductFormats
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import scala.util.Try

/**
 * A marshalling implementation for case classes and TibrvMsg piggybacking on Spray Json for the case class
 * conversion. It spares the complexity of dealing with case class creation. The flip side is the inherently 
 * lossy encoding of JSON.
 */
trait TibrvMsgSprayJsonMarshallingInstances extends ProductFormats with DefaultFormats with AdditionalFormats
  with CollectionFormats with TryExtensionUtil {

  implicit val stringJsFormat = spray.json.DefaultJsonProtocol.StringJsonFormat

  override implicit def optionFormat[T: JsonFormat] = spray.json.DefaultJsonProtocol.optionFormat[T]

  implicit val boolJsFormat = spray.json.DefaultJsonProtocol.BooleanJsonFormat

  implicit val intJsFormat = new JsonFormat[Int] {
    override def write(obj: Int) = JsObject(
      "value" -> JsNumber(obj),
      "$type" -> JsString("int")
    )

    override def read(json: JsValue) =
      json.asInstanceOf[JsNumber].value.toInt
  }

  implicit val longJsFormat = new JsonFormat[Long] {
    override def write(obj: Long) = JsObject(
      "value" -> JsNumber(obj),
      "$type" -> JsString("long")
    )

    override def read(json: JsValue) =
      json.asInstanceOf[JsNumber].value.toLong
  }

  implicit val doubleJsFormat = new JsonFormat[Double] {
    override def write(obj: Double) = JsObject(
      "value" -> JsNumber(obj),
      "$type" -> JsString("double")
    )

    override def read(json: JsValue) =
      json.asInstanceOf[JsNumber].value.toDouble
  }

  val dateTimeClassName = classOf[DateTime].getName

  val localDateClassName = classOf[LocalDate].getName

  implicit val localDateJsFormat = new JsonFormat[LocalDate] {
    override def write(obj: LocalDate) = JsObject(
      "value" -> JsNumber(obj.toDate.getTime),
      "$type" -> JsString(localDateClassName)
    )

    override def read(json: JsValue) =
      new LocalDate(json.asInstanceOf[JsNumber].value.toLong)
  }

  implicit val dateTimeJsFormat = new JsonFormat[DateTime] {
    override def write(obj: DateTime) = JsObject(
      "value" -> JsNumber(obj.toDate.getTime),
      "$type" -> JsString(dateTimeClassName)
    )

    override def read(json: JsValue) =
      new DateTime(json.asInstanceOf[JsNumber].value.toLong)
  }

  implicit def tibrvMsgSprayJsonMarshaller[M: JsonWriter]: BinaryFormatMarshaller[M] = new BinaryFormatMarshaller[M] {
    def marshalToOutputStream(o: M, out: OutputStream) = {

      def jsValueToTibrvMsgField(v: JsValue, m: TibrvMsg, field: String): Unit = v match {
        // JsNumber is intentionally left out here to fail fast if the wrong non-typed JS formats are used
        case JsString(s) =>
          m.add(field, s)
        case JsBoolean(b) =>
          m.add(field, b)
        case a: JsArray =>
          m.add(field, jsArrayToTibrvMsg(a))
        case o: JsObject =>
          o.fields.get("$type") match {
            case Some(JsString(t)) =>
              val n = o.fields("value").asInstanceOf[JsNumber].value
              t match {
                case "double" =>
                  m.add(field, n.toDouble)
                case "int" =>
                  m.add(field, n.toInt)
                case "long" =>
                  m.add(field, n.toLong)
                case `localDateClassName` =>
                  m.add(field, new Date(n.toLong))
                case `dateTimeClassName` =>
                  m.add(field, new Date(n.toLong))
              }
            case _ =>
              m.add(field, jsObjectToTibrvMsg(o))
          }
      }

      def jsObjectToTibrvMsg(o: JsObject) = {
        val m = new TibrvMsg

        for {
          f <- o.fields
        } yield
          jsValueToTibrvMsgField(f._2, m, f._1)

        m
      }

      def jsArrayToTibrvMsg(o: JsArray) = {
        val m = new TibrvMsg

        // Need to add some info to be able to distinguish empty arrays and objects. Unfortunately, there is no
        // single standard here.
        m.add("$type", "JsArray")

        for {
          e <- o.elements
        } yield
          jsValueToTibrvMsgField(e, m, null)

        m
      }

      out.write(jsObjectToTibrvMsg(implicitly[JsonWriter[M]].write(o).asJsObject).getAsBytes)
    }
  }

  implicit def tibrvMsgSprayJsonUnmarshaller[M: JsonReader]: BinaryFormatUnmarshaller[M] = new BinaryFormatUnmarshaller[M] {
    def unmarshalFromInputStream(in: InputStream) = {
      val m = new TibrvMsg(toByteArray(in))

      def tibrvMsgToJsValue(m: TibrvMsg): JsValue =
      // If the message has no fields or the first field has no name, the assumption is that it was 
      // marshalled from a JsArray (fields are indexed rather than named), otherwise assume it was a JsObject
        if (m.getNumFields > 0 && m.getFieldByIndex(0).name == "$type" && m.getFieldByIndex(0).data == "JsArray")
          tibrvMsgToJsArray(m)
        else
          tibrvMsgToJsObject(m)

      def tibrvMsgFieldToJsValue(f: TibrvMsgField) =
        f.data match {
          case s: String =>
            JsString(s)
          case i: Integer =>
            JsNumber(i)
          case b: java.lang.Boolean =>
            JsBoolean(b)
          case i: java.lang.Long =>
            JsNumber(i)
          case i: java.lang.Double =>
            JsNumber(i)
          case d: Date =>
            JsNumber(d.getTime)
          case m: TibrvMsg =>
            tibrvMsgToJsValue(m)
        }

      def tibrvMsgToJsArray(m: TibrvMsg) =
        JsArray(
          for {
            i <- (1 until m.getNumFields).toVector
          } yield tibrvMsgFieldToJsValue(m.getFieldByIndex(i))
        )

      def tibrvMsgToJsObject(m: TibrvMsg) =
        JsObject(
          (for {
            i <- 0 until m.getNumFields
            f = m.getFieldByIndex(i)
          } yield {
            f.name -> tibrvMsgFieldToJsValue(f)
          }).toMap
        )

      Try(tibrvMsgToJsValue(m).convertTo[M]).toEither
    }
  }

  implicit def tibrvMsgSprayJsonMarshalling[T: JsonFormat] = new TibrvMsgSprayJsonMarshalling[T]
}
