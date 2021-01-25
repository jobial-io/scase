package io.jobial.scase.core


sealed trait TestRequest

case class TestRequest1(id: String) extends TestRequest with Request[TestResponse1]

case class TestRequest2(id: String) extends TestRequest // with Request[TestResponse2]

sealed trait TestResponse

case class TestResponse1(request: TestRequest1, greeting: String) extends TestResponse

case class TestResponse2(request: TestRequest2, greeting: String) extends TestResponse
