package io.jobial.scase.aws.client

import com.amazonaws.auth.AWSCredentials

case class AwsContext(
  credentials: Option[AWSCredentials] = None,
  region: Option[String] = None,
  sqsExtendedS3BucketName: Option[String] = None
) {
  
}