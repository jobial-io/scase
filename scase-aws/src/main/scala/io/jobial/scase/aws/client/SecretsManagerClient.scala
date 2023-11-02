package io.jobial.scase.aws.client

import cats.effect.Concurrent
import cats.implicits._
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest
import io.circe.parser
import io.jobial.sprint.util.CatsUtils

trait SecretsManagerClient[F[_]] extends AwsClient[F] with CatsUtils[F] {

  def getSecretValue(id: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.secretsManager.getSecretValueAsync(
      new GetSecretValueRequest()
      .withSecretId(id)
    ))

  def getSecretMap(id: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      r <- getSecretValue(id)
      json <- fromEither(parser.parse(r.getSecretString).flatMap(_.asObject.toRight(new IllegalStateException)))
    } yield json.toMap.mapValues(_.asString).filter(_._2.isDefined).mapValues(_.get).toMap
}

