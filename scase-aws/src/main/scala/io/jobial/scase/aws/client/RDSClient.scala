package io.jobial.scase.aws.client

import cats.effect.Concurrent
import cats.implicits._
import com.amazonaws.services.rds.model.DBCluster
import com.amazonaws.services.rds.model.DescribeDBClustersRequest
import io.jobial.sprint.util.CatsUtils

import scala.collection.JavaConverters._

trait RDSClient[F[_]] extends AwsClient[F] with CatsUtils[F] {

  def describeDBClusters(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.rds.describeDBClustersAsync(
      new DescribeDBClustersRequest
    )).map(_.getDBClusters.asScala.toList)

  implicit val clusterTagged = new Tagged[DBCluster] {
    def tags(tagged: DBCluster) = tagged.getTagList.asScala.toList.map(t => Tag(t.getKey, t.getValue))
  }
}

