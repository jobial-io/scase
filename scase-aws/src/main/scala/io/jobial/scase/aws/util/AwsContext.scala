package io.jobial.scase.aws.util

import com.amazonaws.auth.AWSCredentials

case class AwsContext(
  region: String,
  credentials: Option[AWSCredentials] = None,
  sqsExtendedS3BucketName: Option[String] = None
)