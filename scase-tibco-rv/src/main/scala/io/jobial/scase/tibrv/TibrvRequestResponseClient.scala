///*
// * Copyright (c) 2020 Jobial OÜ. All Rights Reserved.
// *
// * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
// * the License. A copy of the License is located at
// * 
// * http://www.apache.org/licenses/LICENSE-2.0
// * 
// * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
// * and limitations under the License.
// */
//package io.jobial.scase.tibrv
//
//import cats.Monad
//import cats.effect.Concurrent
//import cats.effect.IO
//import io.jobial.scase.core.impl.DefaultMessageSendResult
//import io.jobial.scase.core.impl.DefaultRequestResponseResult
//import io.jobial.scase.core.DefaultMessageReceiveResult
//import io.jobial.scase.core.RequestResponseClient
//import io.jobial.scase.core.RequestResponseMapping
//import io.jobial.scase.core.RequestResponseResult
//import io.jobial.scase.core.SendRequestContext
//import io.jobial.scase.marshalling.Marshaller
//import io.jobial.scase.marshalling.Unmarshaller
//
//import java.nio.charset.StandardCharsets
//import scala.concurrent.ExecutionContext
//
//
//case class TibcoRVRequestResponseClient[F[_] : Concurrent, REQ: Marshaller, RESP: Unmarshaller](
//  functionName: String
//)(
//  implicit val tibcoRVContext: TibcoRVContext,
//  ec: ExecutionContext
//) extends RequestResponseClient[F, REQ, RESP] {
//  
//  
//
//  override def sendRequestWithResponseMapping[REQUEST <: REQ, RESPONSE <: RESP](
//    request: REQUEST,
//    requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]
//  )(
//    implicit sendRequestContext: SendRequestContext
//  ): F[RequestResponseResult[F, REQUEST, RESPONSE]] =
//    Monad[F].pure {
//      DefaultRequestResponseResult(
//        DefaultMessageSendResult[F, REQUEST](Monad[F].unit, Monad[F].unit),
//        DefaultMessageReceiveResult[F, RESPONSE](
//          Concurrent[F].liftIO(
//            for {
//              response <- invoke(functionName, Marshaller[REQ].marshalToText(request))
//              m <- Unmarshaller[RESP].unmarshalFromText(new String(response.getPayload.array, StandardCharsets.UTF_8)) match {
//                case Right(r) =>
//                  IO(r.asInstanceOf[RESPONSE])
//                case Left(t) =>
//                  IO.raiseError(t)
//              }
//            } yield m),
//          Map(), // TODO: propagate attributes here
//          Monad[F].unit,
//          Monad[F].unit
//        )
//      )
//    }
//
//  def stop = Monad[F].unit
//}
//
