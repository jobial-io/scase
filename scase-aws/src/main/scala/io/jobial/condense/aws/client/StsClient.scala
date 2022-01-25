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
package io.jobial.condense.aws.client

import cats.effect.IO
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.amazonaws.services.securitytoken.{AWSSecurityTokenService, AWSSecurityTokenServiceClientBuilder}
import io.jobial.condense.logging.Logging

trait StsClient extends AwsClient with Logging {

  lazy val sts = buildAwsClient[AWSSecurityTokenServiceClientBuilder, AWSSecurityTokenService](AWSSecurityTokenServiceClientBuilder.standard)

  def getAccountId =
    IO(sts.getCallerIdentity(new GetCallerIdentityRequest).getAccount)
}
