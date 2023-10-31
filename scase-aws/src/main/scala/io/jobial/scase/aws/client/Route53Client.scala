package io.jobial.scase.aws.client

import cats.effect.Concurrent
import cats.implicits._
import com.amazonaws.services.route53.model.GetHostedZoneRequest
import com.amazonaws.services.route53.model.ListHostedZonesRequest
import com.amazonaws.services.route53.model.ListResourceRecordSetsRequest
import io.jobial.sprint.util.CatsUtils

import scala.collection.JavaConverters._

trait Route53Client[F[_]] extends AwsClient[F] with CatsUtils[F] {

  def listHostedZones(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.route53.listHostedZonesAsync(
      new ListHostedZonesRequest()
    )).map(_.getHostedZones.asScala.toList)

  def getHostedZone(id: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.route53.getHostedZoneAsync(
      new GetHostedZoneRequest().withId(id)
    ))

  def listResourceRecordSets(id: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(context.route53.listResourceRecordSetsAsync(
      new ListResourceRecordSetsRequest().withHostedZoneId(id)
    )).map(_.getResourceRecordSets.asScala.toList)

  def route53Resolve(name: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      hostedZones <- listHostedZones
      records <- hostedZones.map { z => listResourceRecordSets(z.getId) }.sequence.map(_.flatten)
    } yield records.filter(_.getType === "A").find(_.getName === name)
      .flatMap(_.getResourceRecords.asScala.headOption.map(_.getValue))
}  

