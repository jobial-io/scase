package io.jobial.scase.aws.client

import cats.effect.Concurrent
import com.amazonaws.services.ecs.model.DescribeClustersRequest
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest
import com.amazonaws.services.ecs.model.DescribeServicesRequest
import com.amazonaws.services.ecs.model.DescribeTasksRequest
import io.jobial.sprint.util.CatsUtils

trait ECSClient[F[_]] extends AwsClient[F] with CatsUtils[F] {

  def describeClusters(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeClustersAsync(
      new DescribeClustersRequest()
    ))

  def describeServices(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeServicesAsync(
      new DescribeServicesRequest()
    ))

  def describeTasks(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeTasksAsync(
      new DescribeTasksRequest()
    ))

  def describeContainerInstances(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeContainerInstancesAsync(
      new DescribeContainerInstancesRequest()
    ))
}

