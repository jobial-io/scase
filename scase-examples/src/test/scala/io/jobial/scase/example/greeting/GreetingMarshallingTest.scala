package io.jobial.scase.example.greeting

import cats.effect.IO
import io.jobial.scase.core._
import org.scalatest.flatspec.AsyncFlatSpec

class GreetingMarshallingTest
  extends AsyncFlatSpec
    with ScaseTestHelper
    with GreetingServiceConfig {

  import greetingServiceConfig._

  "Hello marshalling" should "work" in {
    val o = Hello("world")

    for {
      marshalled <- IO(requestMarshaller.marshal(o))
      marshalledText <- IO(requestMarshaller.marshalToText(o))
      unmarshalled <- requestUnmarshaller.unmarshal(marshalled)
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
      marshalled <- IO(responseMarshaller.marshal(o))
      marshalledText <- IO(responseMarshaller.marshalToText(o))
      unmarshalled <- responseUnmarshaller.unmarshal(marshalled)
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
