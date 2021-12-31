package io.jobial.scase.core


sealed trait TestRequest[RESP] extends Request[RESP]

case class TestRequest1(id: String) extends TestRequest[TestResponse1]

case class TestRequest2(id: String) extends TestRequest[TestResponse2]

sealed trait TestResponse

case class TestResponse1(request: TestRequest1, greeting: String) extends TestResponse

case class TestResponse2(request: TestRequest2, greeting: String) extends TestResponse

case class TestException(message: String) extends Exception(message)

trait RequestResponseTestModel {

  val request1 = TestRequest1("1")

  val request2 = TestRequest2("2")

  val response1 = TestResponse1(request1, "1")

  val response2 = TestResponse2(request2, "2")
}