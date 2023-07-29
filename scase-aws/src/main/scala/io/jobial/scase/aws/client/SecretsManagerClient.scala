package io.jobial.scase.aws.client

import cats.Parallel
import cats.effect.Concurrent
import cats.implicits._
import com.amazonaws.services.ec2.model.DescribeFleetInstancesRequest
import com.amazonaws.services.ec2.model.DescribeFleetsRequest
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.DescribeSpotFleetInstancesRequest
import com.amazonaws.services.ec2.model.DescribeSpotFleetRequestsRequest
import com.amazonaws.services.ec2.model.FleetData
import com.amazonaws.services.ec2.model.ModifyFleetRequest
import com.amazonaws.services.ec2.model.ModifySpotFleetRequestRequest
import com.amazonaws.services.ec2.model.SpotFleetRequestConfig
import com.amazonaws.services.ec2.model.StartInstancesRequest
import com.amazonaws.services.ec2.model.StopInstancesRequest
import com.amazonaws.services.ec2.model.TargetCapacitySpecificationRequest
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest
import io.jobial.sprint.util.CatsUtils

import scala.collection.JavaConverters._

trait SecretsManagerClient[F[_]] extends AwsClient[F] with CatsUtils[F] {

  def getInstanceState(id: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    context.secretsManager.getSecretValueAsync(
      new GetSecretValueRequest()
      .withSecretId(id)
    )
}

