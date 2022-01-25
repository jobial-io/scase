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
package io.jobial.scase.example.greeting.pulsar

import io.jobial.scase.core._
import org.scalatest.Assertions.===
import org.scalatest.flatspec.AsyncFlatSpec

//class GreetingMarshallingTest
//  extends AsyncFlatSpec
//    with ScaseTestHelper
//    with GreetingServicePulsarConfig {
//
//  "Hello marshalling" should "work" in {
//    val o = Hello("world")
//
//    for {
//      marshalled <- Right(requestMarshaller.marshal(o))
//      marshalledText <- Right(requestMarshaller.marshalToText(o))
//      unmarshalled <- requestUnmarshaller.unmarshal(marshalled)
//    } yield {
//      assert(marshalledText === """{
//  "Hello" : {
//    "person" : "world"
//  }
//}""")
//      assert(unmarshalled === o)
//    }
//  }
//
//  "HelloResponse marshalling" should "work" in {
//    val o = HelloResponse("hello world")
//
//    for {
//      marshalled <- Right(responseMarshaller.marshal(o))
//      marshalledText <- Right(responseMarshaller.marshalToText(o))
//      unmarshalled <- responseUnmarshaller.unmarshal(marshalled)
//    } yield {
//      assert(marshalledText === """{
//  "HelloResponse" : {
//    "sayingHello" : "hello world"
//  }
//}""")
//      assert(unmarshalled === o)
//    }
//  }
//}
