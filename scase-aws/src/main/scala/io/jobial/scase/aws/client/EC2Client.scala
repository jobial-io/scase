package io.jobial.scase.aws.client

import cats.Parallel
import cats.effect.Concurrent
import cats.effect.Timer
import cats.implicits._
import com.amazonaws.client.builder.ExecutorFactory
import com.amazonaws.endpointdiscovery.DaemonThreadFactory
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.ec2.AmazonEC2AsyncClientBuilder
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
import io.jobial.sprint.util.CatsUtils

import scala.collection.JavaConverters._
import java.util.concurrent.Executors

object EC2Client {

  val client = AmazonEC2AsyncClientBuilder.standard().withExecutorFactory(new ExecutorFactory {
    def newExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory)
  }).build

  def apply[F[_] : Concurrent : Timer : Parallel](implicit context: AwsContext) =
    new S3Client[F] {
      def awsContext = context

      val concurrent = Concurrent[F]

      val timer = Timer[F]

      val parallel = Parallel[F]
    }
}

trait EC2Client[F[_]] extends AwsClient[F] with CatsUtils[F] {

  def ec2Client = buildAwsAsyncClient[AmazonEC2AsyncClientBuilder, AmazonEC2Async](AmazonEC2AsyncClientBuilder.standard)

  def getInstanceState(id: String) =
    for {
      r <- fromJavaFuture(ec2Client.describeInstancesAsync(
        new DescribeInstancesRequest().withInstanceIds(id)
      ))
    } yield r.getReservations.asScala.flatMap(_.getInstances.asScala).headOption.map(_.getState)

  def startInstance(id: String) =
    fromJavaFuture(ec2Client.startInstancesAsync(
      new StartInstancesRequest().withInstanceIds(id)
    ))

  def stopInstance(id: String) =
    fromJavaFuture(ec2Client.stopInstancesAsync(
      new StopInstancesRequest().withInstanceIds(id)
    ))

  def describeSpotFleetRequests =
    fromJavaFuture(ec2Client.describeSpotFleetRequestsAsync(
      new DescribeSpotFleetRequestsRequest()
    ))

  def describeFleets =
    fromJavaFuture(ec2Client.describeFleetsAsync(
      new DescribeFleetsRequest()
    ))

  def modifySpotFleetRequests(request: ModifySpotFleetRequestRequest) =
    fromJavaFuture(ec2Client
      .modifySpotFleetRequestAsync(request))

  def setSpotFleetTargetCapacity(id: String, capacity: Int) =
    modifySpotFleetRequests(new ModifySpotFleetRequestRequest()
      .withSpotFleetRequestId(id).withTargetCapacity(capacity))

  def describeSpotFleetInstances(spotFleetRequestId: String) =
    fromJavaFuture(ec2Client.describeSpotFleetInstancesAsync(
      new DescribeSpotFleetInstancesRequest().withSpotFleetRequestId(spotFleetRequestId)
    ))

  def describeFleetInstances(fleetId: String) =
    fromJavaFuture(ec2Client.describeFleetInstancesAsync(
      new DescribeFleetInstancesRequest().withFleetId(fleetId)
    ))

  def targetCapacity(config: SpotFleetRequestConfig) =
    config.getSpotFleetRequestConfig.getTargetCapacity.toInt

  def spotTargetCapacity(config: FleetData) =
    config.getTargetCapacitySpecification.getSpotTargetCapacity.toInt

  def getSpotRequestSpotInstanceState(spotFleetRequestId: String) =
    for {
      r <- describeSpotFleetInstances(spotFleetRequestId)
      state <- r.getActiveInstances.asScala.headOption.map(_.getInstanceId).map(getInstanceState)
        .parSequence.map(_.flatten)
    } yield state.map(_.getName)

  def getFleetInstanceState(fleetId: String) =
    for {
      r <- describeFleetInstances(fleetId)
      state <- r.getActiveInstances.asScala.headOption.map(_.getInstanceId).map(getInstanceState)
        .parSequence.map(_.flatten)
    } yield state.map(_.getName)

  def setFleetSpotTargetCapacity(id: String, capacity: Int) =
    for {
      r <- fromJavaFuture(ec2Client.modifyFleetAsync(
        new ModifyFleetRequest()
          .withFleetId(id).withTargetCapacitySpecification(
          new TargetCapacitySpecificationRequest()
            .withSpotTargetCapacity(capacity)
            .withTotalTargetCapacity(capacity)
        ))
      )
    } yield r

  protected implicit def parallel: Parallel[F]
}

