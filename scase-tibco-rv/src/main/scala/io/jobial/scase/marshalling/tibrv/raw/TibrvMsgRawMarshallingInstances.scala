package io.jobial.scase.marshalling.tibrv.raw

import com.tibco.tibrv.TibrvMsg
import io.jobial.scase.marshalling.BinaryFormatMarshaller
import io.jobial.scase.marshalling.BinaryFormatUnmarshaller
import org.apache.commons.io.IOUtils
import org.apache.commons.io.IOUtils.toByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import scala.util.Try
import io.jobial.scase.util._

trait TibrvMsgRawMarshallingInstances {

  implicit val tibrvMsgRawMarshaller = new BinaryFormatMarshaller[TibrvMsg] {
    def marshalToOutputStream(o: TibrvMsg, out: OutputStream) =
      out.write(o.getAsBytes)
  }

  implicit val tibrvMsgRawUnmarshaller = new BinaryFormatUnmarshaller[TibrvMsg] {
    def unmarshalFromInputStream(in: InputStream) =
      Try(new TibrvMsg(toByteArray(in))).toEither
  }

  implicit val tibrvMsgRawThrowableMarshaller = new BinaryFormatMarshaller[Throwable] {
    def marshalToOutputStream(o: Throwable, out: OutputStream) = {
      val msg = new TibrvMsg
      // TODO: improve this
      msg.add("error", o.getMessage)
      out.write(msg.getAsBytes)
    }
  }

  implicit val tibrvMsgRawThrowableUnmarshaller = new BinaryFormatUnmarshaller[Throwable] {
    def unmarshalFromInputStream(in: InputStream): Either[Throwable, Throwable] = {
      for {
        msg <- Try(new TibrvMsg(toByteArray(in)))
      } yield new RuntimeException(msg.get("error").toString)
    }.toEither
  }

  implicit val tibrvMsgRawEitherMarshaller = new BinaryFormatMarshaller[Either[Throwable, TibrvMsg]] {
    def marshalToOutputStream(o: Either[Throwable, TibrvMsg], out: OutputStream) = o match {
      case Left(t) =>
        tibrvMsgRawThrowableMarshaller.marshalToOutputStream(t, out)
      case Right(m) =>
        tibrvMsgRawMarshaller.marshalToOutputStream(m, out)
    }
  }

  implicit val tibrvMsgRawEitherUnmarshaller = new BinaryFormatUnmarshaller[Either[Throwable, TibrvMsg]] {
    def marshalToOutputStream(o: Either[Throwable, TibrvMsg], out: OutputStream) = o match {
      case Left(t) =>
        tibrvMsgRawThrowableMarshaller.marshalToOutputStream(t, out)
      case Right(m) =>
        tibrvMsgRawMarshaller.marshalToOutputStream(m, out)
    }

    def unmarshalFromInputStream(in: InputStream): Either[Throwable, Either[Throwable, TibrvMsg]] = {
      val out = new ByteArrayOutputStream
      IOUtils.copy(in, out)
      val buf = out.toByteArray
      tibrvMsgRawThrowableUnmarshaller.unmarshalFromInputStream(new ByteArrayInputStream(buf)) match {
        case Left(_) =>
          tibrvMsgRawUnmarshaller.unmarshalFromInputStream(new ByteArrayInputStream(buf)).right.map(Right(_))
        case Right(t) =>
          Right(Left[Throwable, TibrvMsg](t))
      }
    }
  }
  
  implicit val tibrvMsgRawMarshalling = new TibrvMsgRawMarshalling 
}