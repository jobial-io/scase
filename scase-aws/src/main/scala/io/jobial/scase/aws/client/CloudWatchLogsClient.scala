package io.jobial.scase.aws.client

import cats.effect.Concurrent
import cats.implicits._
import com.amazonaws.services.logs.model.DescribeLogGroupsRequest
import com.amazonaws.services.logs.model.DescribeLogStreamsRequest
import com.amazonaws.services.logs.model.OrderBy
import io.jobial.sprint.util.CatsUtils

import scala.collection.JavaConverters._

trait CloudWatchLogsClient[F[_]] extends AwsClient[F] with CatsUtils[F] {
  
  def describeLogGroups(limit: Int = 10000)(implicit awsContext: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(awsContext.logs.describeLogGroupsAsync(
      new DescribeLogGroupsRequest().withLimit(limit)
    )).map(_.getLogGroups.asScala.toList)

  def describeLogStreams(logGroup: String, limit: Int = 10)(implicit awsContext: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(awsContext.logs.describeLogStreamsAsync(
      new DescribeLogStreamsRequest().withLogGroupName(logGroup).withLimit(limit)
        .withOrderBy(OrderBy.LastEventTime).withDescending(true)
    )).map(_.getLogStreams.asScala.toList)
}  

