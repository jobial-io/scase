package io.jobial.scase.example.greeting

import cats.effect.IO
import io.jobial.scase.core._
import org.scalatest.flatspec.AsyncFlatSpec

class GreetingMarshallingTest
  extends AsyncFlatSpec
    with ScaseTestHelper
    with GreetingServiceConfig {

  "Hello marshalling" should "work" in {
    val o = Hello("world")

    for {
      marshalled <- IO(greetingServiceConfig.requestMarshaller.marshal(o))
      marshalledText <- IO(greetingServiceConfig.requestMarshaller.marshalToText(o))
      unmarshalled <- greetingServiceConfig.requestUnmarshaller.unmarshal(marshalled)
    } yield {
      assert(marshalledText === """{
  "Hello" : {
    "person" : "world"
  }
}""")
      assert(unmarshalled === o)
    }
  }

  "HelloResponse marshalling" should "work" in {
    val o = HelloResponse("hello world")

    for {
      marshalled <- IO(greetingServiceConfig.responseMarshaller.marshal(o))
      marshalledText <- IO(greetingServiceConfig.responseMarshaller.marshalToText(o))
      unmarshalled <- greetingServiceConfig.responseUnmarshaller.unmarshal(marshalled)
    } yield {
      assert(marshalledText === """{
  "HelloResponse" : {
    "sayingHello" : "hello world"
  }
}""")
      assert(unmarshalled === o)
    }
  }
}
