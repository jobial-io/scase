package io.jobial.scase.example.greeting

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
      marshalled <- Right(requestMarshaller.marshal(o))
      marshalledText <- Right(requestMarshaller.marshalToText(o))
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
      marshalled <- Right(responseMarshaller.marshal(o))
      marshalledText <- Right(responseMarshaller.marshalToText(o))
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
