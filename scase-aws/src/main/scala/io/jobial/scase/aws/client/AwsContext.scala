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

import cats.effect.IO
import com.amazonaws.auth.AWSCredentials
import scala.concurrent.ExecutionContext.Implicits.global

case class AwsContext(
  credentials: Option[AWSCredentials] = None,
  region: Option[String] = sys.env.get("AWS_DEFAULT_REGION"),
  sqsExtendedS3BucketName: Option[String] = None
) {

  implicit val awsContext = this

  implicit val contextShift = IO.contextShift(global)
  
  implicit val timer = IO.timer(global)

  // Amazon recommends sharing and reusing clients  
  lazy val sqsClient = SqsClient[IO]

  lazy val lambdaClient = LambdaClient[IO]

  lazy val stsClient = StsClient[IO]

  lazy val s3Client = S3Client[IO]
}