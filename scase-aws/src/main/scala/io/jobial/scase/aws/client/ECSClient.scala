package io.jobial.scase.aws.client

import cats.effect.Concurrent
import cats.implicits._
import com.amazonaws.services.ecs.model.DescribeClustersRequest
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest
import com.amazonaws.services.ecs.model.DescribeServicesRequest
import com.amazonaws.services.ecs.model.DescribeTasksRequest
import com.amazonaws.services.ecs.model.InvalidParameterException
import com.amazonaws.services.ecs.model.ListClustersRequest
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest
import com.amazonaws.services.ecs.model.ListServicesRequest
import com.amazonaws.services.ecs.model.ListTagsForResourceRequest
import com.amazonaws.services.ecs.model.ListTagsForResourceResult
import com.amazonaws.services.ecs.model.ListTasksRequest
import com.amazonaws.services.ecs.model.StopTaskRequest
import com.amazonaws.services.ecs.model.StopTaskResult
import com.amazonaws.services.ecs.model.Task
import io.jobial.sprint.util.CatsUtils

import scala.collection.JavaConverters._

trait ECSClient[F[_]] extends AwsClient[F] with CatsUtils[F] {

  def listClusters(limit: Int = 1000)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    getPaginatedResult { nextToken =>
      fromJavaFuture(context.ecs.listClustersAsync {
        val request = new ListClustersRequest()
        nextToken.map(request.withNextToken).getOrElse(request)
      })
    }(_.getClusterArns, _.getNextToken, limit)

  def describeClusters(clusters: List[String])(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeClustersAsync(
      new DescribeClustersRequest().withClusters(clusters.asJava)
    ))

  def describeAllClusters(limit: Int = 1000)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      clusters <- listClusters(limit)
      clusters <- describeClusters(clusters.toList)
    } yield clusters.getClusters.asScala.toList

  def listServices(clusterId: String, limit: Int = 1000)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    getPaginatedResult { nextToken =>
      fromJavaFuture(context.ecs.listServicesAsync {
        val request = new ListServicesRequest().withCluster(clusterId)
        nextToken.map(request.withNextToken).getOrElse(request)
      })
    }(_.getServiceArns, _.getNextToken, limit)

  def describeServices(clusterId: String, services: List[String])(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeServicesAsync(
      new DescribeServicesRequest().withCluster(clusterId).withServices(services.asJava)
    ))

  def describeAllServices(clusterId: String)(implicit context: AwsContext, concurrent: Concurrent[F]) = {
    for {
      services <- listServices(clusterId)
      services <- describeServices(clusterId, services.toList)
    } yield services.getServices.asScala.toList
  }.recover { case t: InvalidParameterException => List() }

  def listTasks(clusterId: String, limit: Int = 1000)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    getPaginatedResult { nextToken =>
      fromJavaFuture(context.ecs.listTasksAsync {
        val request = new ListTasksRequest().withCluster(clusterId)
        nextToken.map(request.withNextToken).getOrElse(request)
      })
    }(_.getTaskArns, _.getNextToken, limit)

  def describeTasks(clusterId: String, tasks: List[String])(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeTasksAsync(
      new DescribeTasksRequest().withCluster(clusterId).withTasks(tasks.asJava)
    ))

  def describeAllTasks(clusterId: String)(implicit context: AwsContext, concurrent: Concurrent[F]) = {
    for {
      tasks <- listTasks(clusterId)
      tasks <- describeTasks(clusterId, tasks.toList)
    } yield tasks.getTasks.asScala.toList
  }.recover { case t: InvalidParameterException => List() }

  def listContainerInstances(clusterId: String, limit: Int = 1000)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    getPaginatedResult { nextToken =>
      fromJavaFuture(context.ecs.listContainerInstancesAsync {
        val request = new ListContainerInstancesRequest().withCluster(clusterId)
        nextToken.map(request.withNextToken).getOrElse(request)
      })
    }(_.getContainerInstanceArns, _.getNextToken, limit)

  def describeContainerInstances(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeContainerInstancesAsync(
      new DescribeContainerInstancesRequest()
    ))

  def describeContainerInstances(clusterId: String, containerInstances: List[String])(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.describeContainerInstancesAsync(
      new DescribeContainerInstancesRequest()
        .withCluster(clusterId)
        .withContainerInstances(containerInstances.asJava)
    ))

  def describeAllContainerInstances(clusterId: String)(implicit awsContext: AwsContext, concurrent: Concurrent[F]) =
    for {
      containerInstances <- listContainerInstances(clusterId)
      containerInstances <- describeContainerInstances(clusterId, containerInstances.toList)
    } yield containerInstances.getContainerInstances.asScala.toList

  def listTagsForResource(resourceArn: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecs.listTagsForResourceAsync(
      new ListTagsForResourceRequest().withResourceArn(resourceArn)
    ))

  implicit val listTagsForResourceResultTagged = new Tagged[ListTagsForResourceResult] {
    def tags(tagged: ListTagsForResourceResult) = tagged.getTags.asScala.toList.map(t => Tag(t.getKey, t.getValue))
  }

  def stopTask(task: Task)(implicit awsContext: AwsContext, concurrent: Concurrent[F]): F[StopTaskResult] =
    stopTask(task.getClusterArn, task.getTaskArn)

  def stopTask(cluster: String, taskArn: String)(implicit awsContext: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(awsContext.ecs.stopTaskAsync(new StopTaskRequest().withCluster(cluster).withTask(taskArn)))
}

