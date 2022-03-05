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
package io.jobial.scase.core

import cats.Eq

sealed trait TestRequest[RESP] extends Request[RESP]

case class TestRequest1(id: String) extends TestRequest[TestResponse1]

case class TestRequest2(id: String) extends TestRequest[TestResponse2]

sealed trait TestResponse

case class TestResponse1(request: TestRequest1, greeting: String) extends TestResponse

case class TestResponse2(request: TestRequest2, greeting: String) extends TestResponse

case class TestException(message: String) extends Exception(message)

sealed trait Req

sealed trait Resp

case class Req1() extends Req

case class Resp1() extends Resp

trait ServiceTestModel {

  val request1 = TestRequest1("1")

  val request2 = TestRequest2("2")

  val response1 = TestResponse1(request1, "1")

  val response2 = TestResponse2(request2, "2")

  implicit val eqTestResponse: Eq[TestResponse] = Eq.fromUniversalEquals

  implicit val eqTestResponse1: Eq[TestResponse1] = Eq.fromUniversalEquals

  implicit val eqTestResponse2: Eq[TestResponse2] = Eq.fromUniversalEquals
  
  implicit val eqThrowable: Eq[Throwable] = Eq.fromUniversalEquals

  implicit def req1Resp1Mapping = new RequestResponseMapping[Req1, Resp1] {}

  implicit val eqTestResp: Eq[Resp] = Eq.fromUniversalEquals

  implicit val eqTestResp1: Eq[Resp1] = Eq.fromUniversalEquals
}