package io.jobial.scase.aws.client

import cats.effect.Concurrent
import cats.implicits._
import com.amazonaws.services.logs.model.DescribeLogStreamsRequest
import io.jobial.sprint.util.CatsUtils

import scala.collection.JavaConverters._

trait CloudWatchLogsClient[F[_]] extends AwsClient[F] with CatsUtils[F] {
  
  def describeLogGroups(implicit awsContext: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(awsContext.logs.describeLogGroupsAsync()).map(_.getLogGroups.asScala.toList)

  def describeLogStreams(logGroup: String)(implicit awsContext: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(awsContext.logs.describeLogStreamsAsync(
      new DescribeLogStreamsRequest().withLogGroupName(logGroup)
    )).map(_.getLogStreams.asScala.toList)
}  

