package io.jobial.scase.aws.lambda

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO}
import io.jobial.scase.aws.util.AwsContext
import io.jobial.scase.core.{MessageReceiveResult, RequestResponseClient, RequestResponseMapping, RequestResult, SendRequestContext}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import scala.concurrent.ExecutionContext

case class LambdaRequestResponseClient[REQ: Marshaller, RESP: Unmarshaller](
  functionName: String
)(
  implicit val awsContext: AwsContext,
  val cs: ContextShift[IO]
) extends RequestResponseClient[REQ, RESP] with LambdaClient {

  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)

  override def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](
    request: REQUEST
  )(
    implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE],
    sendRequestContext: SendRequestContext
  ): RequestResult[RESPONSE] = LambdaRequestResult(
    IO.fromFuture(
      IO(
        for {
          result <- invoke(functionName, Marshaller[REQ].marshalToText(request))
        } yield
          Unmarshaller[RESP].unmarshalFromText(new String(result.getPayload.array, "utf-8")).asInstanceOf[RESPONSE]
      )
    )
  )
}

case class LambdaRequestResult[RESPONSE](resp: IO[RESPONSE]) extends RequestResult[RESPONSE] {

  def response =
    for {
      resp <- resp
    } yield MessageReceiveResult[RESPONSE](
      resp,
      Map(), // TODO: propagate attributes here
      commit = () => IO(),
      rollback = () => IO()
    )

  def commit = IO()
}
