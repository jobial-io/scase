package io.jobial.scase.tools.listen

import io.jobial.scase.marshalling.BinaryFormatUnmarshaller

import java.io.InputStream

class TibrvMsgUnmarshaller extends BinaryFormatUnmarshaller[String] {
  
  def unmarshalFromInputStream(in: InputStream) =
    io.jobial.scase.marshalling.tibrv.raw.tibrvMsgRawUnmarshaller.unmarshalFromInputStream(in).right.map(_.toString)
}
