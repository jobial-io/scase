package io.jobial.scase.aws.client

import cats.effect.IO
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.amazonaws.services.securitytoken.{AWSSecurityTokenService, AWSSecurityTokenServiceClientBuilder}
import io.jobial.scase.logging.Logging

trait StsClient extends AwsClient with Logging {

  lazy val sts = buildAwsClient[AWSSecurityTokenServiceClientBuilder, AWSSecurityTokenService](AWSSecurityTokenServiceClientBuilder.standard)

  def getAccount =
    IO(sts.getCallerIdentity(new GetCallerIdentityRequest).getAccount)
}
