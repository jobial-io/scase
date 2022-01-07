package io.jobial.scase.aws.client

import com.amazonaws.auth.AWSCredentials

case class AwsContext(
  credentials: Option[AWSCredentials] = None,
  region: Option[String] = sys.env.get("AWS_DEFAULT_REGION"),
  sqsExtendedS3BucketName: Option[String] = None
) {
  
  lazy val sqsClient = new SqsClient {
    def awsContext: AwsContext = AwsContext.this
  }

  lazy val lambdaClient = new LambdaClient {
    def awsContext: AwsContext = AwsContext.this
  }
  
  lazy val stsClient = new StsClient {
    override def awsContext: AwsContext = AwsContext.this
  }
}