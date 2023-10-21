package io.jobial.scase.aws.client

import cats.effect.Concurrent
import com.amazonaws.services.ecs.model.Cluster
import com.amazonaws.services.ecs.model.ContainerInstance
import com.amazonaws.services.ecs.model.DescribeClustersRequest
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest
import com.amazonaws.services.ecs.model.DescribeServicesRequest
import com.amazonaws.services.ecs.model.DescribeTasksRequest
import com.amazonaws.services.ecs.model.ListClustersRequest
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest
import com.amazonaws.services.ecs.model.ListServicesRequest
import com.amazonaws.services.ecs.model.ListTasksRequest
import com.amazonaws.services.ecs.model.Service
import com.amazonaws.services.ecs.model.Task
import io.jobial.sprint.util.CatsUtils

import scala.jdk.CollectionConverters.IterableHasAsScala

trait ECSClient[F[_]] extends AwsClient[F] with CatsUtils[F] {

  def listClusters(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.listClustersAsync(
      new ListClustersRequest()
    ))

  def describeClusters(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeClustersAsync(
      new DescribeClustersRequest()
    ))

  implicit val clusterTagged = new Tagged[Cluster] {
    def tags(tagged: Cluster) = tagged.getTags.asScala.toList.map(t => Tag(t.getKey, t.getValue))
  }

  def listServices(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.listServicesAsync(
      new ListServicesRequest()
    ))

  def describeServices(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeServicesAsync(
      new DescribeServicesRequest()
    ))

  implicit val serviceTagged = new Tagged[Service] {
    def tags(tagged: Service) = tagged.getTags.asScala.toList.map(t => Tag(t.getKey, t.getValue))
  }

  def listTasks(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.listTasksAsync(
      new ListTasksRequest()
    ))

  def describeTasks(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeTasksAsync(
      new DescribeTasksRequest()
    ))

  implicit val taskTagged = new Tagged[Task] {
    def tags(tagged: Task) = tagged.getTags.asScala.toList.map(t => Tag(t.getKey, t.getValue))
  }

  def listContainerInstances(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.listContainerInstancesAsync(
      new ListContainerInstancesRequest()
    ))

  def describeContainerInstances(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeContainerInstancesAsync(
      new DescribeContainerInstancesRequest()
    ))

  implicit val containerInstanceTagged = new Tagged[ContainerInstance] {
    def tags(tagged: ContainerInstance) = tagged.getTags.asScala.toList.map(t => Tag(t.getKey, t.getValue))
  }
}

