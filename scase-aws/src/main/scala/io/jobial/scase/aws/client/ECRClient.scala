package io.jobial.scase.aws.client

import cats.Parallel
import cats.effect.Concurrent
import cats.implicits._
import com.amazonaws.services.ecr.model.DescribeImagesRequest
import com.amazonaws.services.ecr.model.DescribeRepositoriesRequest
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest
import io.circe.parser
import io.jobial.sprint.util.CatsUtils

import scala.collection.JavaConverters._

trait ECRClient[F[_]] extends AwsClient[F] with CatsUtils[F] {

  def describeRepositories(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecr.describeRepositoriesAsync(
      new DescribeRepositoriesRequest
    )).map(_.getRepositories.asScala.toList)

  def describeImages(repositoryName: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.ecr.describeImagesAsync(
      new DescribeImagesRequest().withRepositoryName(repositoryName)
    )).map(_.getImageDetails.asScala.toList)
    
  def describeAllImages(implicit context: AwsContext, concurrent: Concurrent[F], parallel: Parallel[F]) =
    for {
      repositories <- describeRepositories
      images <- repositories.map(repository => describeImages(repository.getRepositoryName)).parSequence
    } yield repositories.zip(images)
}

