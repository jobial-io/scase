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
package io.jobial.scase.aws.client

import cats.effect.unsafe.IORuntime
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import io.jobial.scase.core.impl.TemporalEffect

trait StsClient[F[_]] extends AwsClient[F] {

  lazy val sts = buildAwsClient[AWSSecurityTokenServiceClientBuilder, AWSSecurityTokenService](AWSSecurityTokenServiceClientBuilder.standard)

  def getAccountId =
    delay(sts.getCallerIdentity(new GetCallerIdentityRequest).getAccount)
}

object StsClient {

  def apply[F[_] : TemporalEffect](implicit context: AwsContext) =
    new StsClient[F] {
      def awsContext = context

      val temporal = TemporalEffect[F]

      val runtime = IORuntime.global
    }
}