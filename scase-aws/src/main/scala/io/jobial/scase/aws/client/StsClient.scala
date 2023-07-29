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

import cats.effect.Concurrent
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest

trait StsClient[F[_]] extends AwsClient[F] {

  def getAccountId(implicit context: AwsContext, concurrent: Concurrent[F]) =
    delay(context.sts.getCallerIdentity(new GetCallerIdentityRequest).getAccount)
}
