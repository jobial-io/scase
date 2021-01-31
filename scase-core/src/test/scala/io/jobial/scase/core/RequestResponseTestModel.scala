package io.jobial.scase.core


sealed trait TestRequest[RESP] extends Request[RESP]

case class TestRequest1(id: String) extends TestRequest[TestResponse1]

case class TestRequest2(id: String) extends TestRequest[TestResponse2]

sealed trait TestResponse

case class TestResponse1(request: TestRequest1, greeting: String) extends TestResponse

case class TestResponse2(request: TestRequest2, greeting: String) extends TestResponse
