package io.jobial.scase.aws.client

import cats.effect.Concurrent
import com.amazonaws.services.route53.model.GetHostedZoneRequest
import com.amazonaws.services.route53.model.ListHostedZonesRequest
import com.amazonaws.services.route53.model.ListResourceRecordSetsRequest
import io.jobial.sprint.util.CatsUtils

trait Route53Client[F[_]] extends AwsClient[F] with CatsUtils[F] {

  def listHostedZones(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.route53.listHostedZonesAsync(
      new ListHostedZonesRequest()
    ))

  def getHostedZone(id: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.route53.getHostedZoneAsync(
      new GetHostedZoneRequest().withId(id)
    ))

  def listResourceRecordSets(id: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.route53.listResourceRecordSetsAsync(
      new ListResourceRecordSetsRequest().withHostedZoneId(id)
    ))
}  

