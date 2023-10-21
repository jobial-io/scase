package io.jobial.scase.aws.client

import cats.Parallel
import cats.effect.Concurrent
import cats.implicits._
import com.amazonaws.services.ec2.model.DescribeFleetInstancesRequest
import com.amazonaws.services.ec2.model.DescribeFleetsRequest
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.DescribeSpotFleetInstancesRequest
import com.amazonaws.services.ec2.model.DescribeSpotFleetRequestsRequest
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest
import com.amazonaws.services.ec2.model.FleetData
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.ModifyFleetRequest
import com.amazonaws.services.ec2.model.ModifySpotFleetRequestRequest
import com.amazonaws.services.ec2.model.RebootInstancesRequest
import com.amazonaws.services.ec2.model.SpotFleetRequestConfig
import com.amazonaws.services.ec2.model.StartInstancesRequest
import com.amazonaws.services.ec2.model.StopInstancesRequest
import com.amazonaws.services.ec2.model.TargetCapacitySpecificationRequest
import io.jobial.scase.aws.client.Tagged.TaggedSyntax
import io.jobial.sprint.util.CatsUtils

import scala.collection.JavaConverters._

trait EC2Client[F[_]] extends AwsClient[F] with CatsUtils[F] {

  def describeInstances(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ec2.describeInstancesAsync(
      new DescribeInstancesRequest()
    ))

  def listInstances(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      r <- describeInstances
    } yield
      r.getReservations.asScala.toList.flatMap(_.getInstances.asScala)

  def describeInstance(id: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ec2.describeInstancesAsync(
      new DescribeInstancesRequest().withInstanceIds(id)
    ))

  def getInstanceState(id: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      r <- describeInstance(id)
    } yield r.getReservations.asScala.flatMap(_.getInstances.asScala).headOption.map(_.getState)

  def startInstance(id: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ec2.startInstancesAsync(
      new StartInstancesRequest().withInstanceIds(id)
    ))

  def stopInstance(id: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ec2.stopInstancesAsync(
      new StopInstancesRequest().withInstanceIds(id)
    ))

  def rebootInstance(id: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ec2.rebootInstancesAsync(
      new RebootInstancesRequest().withInstanceIds(id)
    ))

  def describeSpotInstanceRequests(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ec2.describeSpotInstanceRequestsAsync(
      new DescribeSpotInstanceRequestsRequest()
    ))

  def describeSpotFleetRequests(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ec2.describeSpotFleetRequestsAsync(
      new DescribeSpotFleetRequestsRequest()
    ))

  def describeFleets(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ec2.describeFleetsAsync(
      new DescribeFleetsRequest()
    ))

  def modifySpotFleetRequests(request: ModifySpotFleetRequestRequest)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ec2
      .modifySpotFleetRequestAsync(request))

  def setSpotFleetTargetCapacity(id: String, capacity: Int)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    modifySpotFleetRequests(new ModifySpotFleetRequestRequest()
      .withSpotFleetRequestId(id).withTargetCapacity(capacity))

  def describeSpotFleetInstances(spotFleetRequestId: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ec2.describeSpotFleetInstancesAsync(
      new DescribeSpotFleetInstancesRequest().withSpotFleetRequestId(spotFleetRequestId)
    ))

  def describeFleetInstances(fleetId: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ec2.describeFleetInstancesAsync(
      new DescribeFleetInstancesRequest().withFleetId(fleetId)
    ))

  def targetCapacity(config: SpotFleetRequestConfig) =
    config.getSpotFleetRequestConfig.getTargetCapacity.toInt

  def spotTargetCapacity(config: FleetData) =
    config.getTargetCapacitySpecification.getSpotTargetCapacity.toInt

  def getSpotRequestSpotInstanceState(spotFleetRequestId: String)(implicit context: AwsContext, concurrent: Concurrent[F], parallel: Parallel[F]) =
    for {
      r <- describeSpotFleetInstances(spotFleetRequestId)
      state <- r.getActiveInstances.asScala.headOption.map(_.getInstanceId).map(getInstanceState)
        .parSequence.map(_.flatten)
    } yield state.map(_.getName)

  def getFleetInstanceState(fleetId: String)(implicit context: AwsContext, concurrent: Concurrent[F], parallel: Parallel[F]) =
    for {
      r <- describeFleetInstances(fleetId)
      state <- r.getActiveInstances.asScala.headOption.map(_.getInstanceId).map(getInstanceState)
        .parSequence.map(_.flatten)
    } yield state.map(_.getName)

  def setFleetSpotTargetCapacity(id: String, capacity: Int)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      r <- fromJavaFuture(context.ec2.modifyFleetAsync(
        new ModifyFleetRequest()
          .withFleetId(id).withTargetCapacitySpecification(
            new TargetCapacitySpecificationRequest()
              .withSpotTargetCapacity(capacity)
              .withTotalTargetCapacity(capacity)
          ))
      )
    } yield r

  implicit val instanceTagged = new Tagged[Instance] {
    def tags(tagged: Instance) = tagged.getTags.asScala.toList.map(t => Tag(t.getKey, t.getValue))
  }

  def findLiveInstanceByTag(key: String, value: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      instances <- listInstances
    } yield instances.filter(_.getState.getName =!= "terminated")
      .find(_.tagValue(key) === Some(value))
}  

