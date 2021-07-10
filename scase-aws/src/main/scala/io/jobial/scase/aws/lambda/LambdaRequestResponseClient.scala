package io.jobial.scase.aws.lambda

import cats.Monad

import java.util.concurrent.Executors
import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import io.jobial.scase.aws.util.AwsContext
import io.jobial.scase.core.{MessageReceiveResult, RequestResponseClient, RequestResponseMapping, RequestResult, SendRequestContext}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import scala.concurrent.ExecutionContext

case class LambdaRequestResponseClient[F[_], REQ: Marshaller, RESP: Unmarshaller](
  functionName: String
)(
  implicit val awsContext: AwsContext,
  val concurrent: Concurrent[F]
) extends RequestResponseClient[F, REQ, RESP] with LambdaClient {

  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)

  override def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](
    request: REQUEST,
    requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]
  )(
    implicit sendRequestContext: SendRequestContext
  ): RequestResult[F, RESPONSE] = LambdaRequestResult(
    Concurrent[F].async { ready =>
      (for {
        result <- invoke(functionName, Marshaller[REQ].marshalToText(request))
      } yield
        ready(Right(Unmarshaller[RESP].unmarshalFromText(new String(result.getPayload.array, "utf-8")).asInstanceOf[RESPONSE]))) recover {
        case t =>
          ready(Left(t))
      }
        
    }
  )
}

case class LambdaRequestResult[F[_], RESPONSE](resp: F[RESPONSE])(implicit m: Monad[F]) extends RequestResult[F, RESPONSE] {

  def response =
    for {
      resp <- resp
    } yield MessageReceiveResult[F, RESPONSE](
      resp,
      Map(), // TODO: propagate attributes here
      commit = () => Monad[F].unit,
      rollback = () => Monad[F].unit
    )

  def commit = Monad[F].unit
}
