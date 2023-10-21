package io.jobial.scase.aws.client

import cats.implicits._
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

import scala.collection.JavaConverters._

trait ECSClient[F[_]] extends AwsClient[F] with CatsUtils[F] {

  def listClusters(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.listClustersAsync(
      new ListClustersRequest()
    ))

  def describeClusters(clusters: List[String])(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeClustersAsync(
      new DescribeClustersRequest().withClusters(clusters.asJava)
    ))
    
  def describeAllClusters(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      clusters <- listClusters
      clusters <- describeClusters(clusters.getClusterArns.asScala.toList)
    } yield clusters.getClusters.asScala.toList
  
  implicit val clusterTagged = new Tagged[Cluster] {
    def tags(tagged: Cluster) = tagged.getTags.asScala.toList.map(t => Tag(t.getKey, t.getValue))
  }

  def listServices(clusterId: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.listServicesAsync(
      new ListServicesRequest().withCluster(clusterId)
    ))

  def describeServices(clusterId: String, services: List[String])(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeServicesAsync(
      new DescribeServicesRequest().withCluster(clusterId).withServices(services.asJava)
    ))

  def describeAllServices(clusterId: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      services <- listServices(clusterId)
      services <- describeServices(clusterId, services.getServiceArns.asScala.toList)
    } yield services.getServices.asScala.toList

  implicit val serviceTagged = new Tagged[Service] {
    def tags(tagged: Service) = tagged.getTags.asScala.toList.map(t => Tag(t.getKey, t.getValue))
  }

  def listTasks(clusterId: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.listTasksAsync(
      new ListTasksRequest().withCluster(clusterId)
    ))

  def describeTasks(clusterId: String, tasks: List[String])(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeTasksAsync(
      new DescribeTasksRequest().withCluster(clusterId).withTasks(tasks.asJava)
    ))

  def describeAllTasks(clusterId: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      tasks <- listTasks(clusterId)
      tasks <- describeTasks(clusterId, tasks.getTaskArns.asScala.toList)
    } yield tasks.getTasks.asScala.toList

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

