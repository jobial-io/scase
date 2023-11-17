package io.jobial.scase.aws.client

import cats.effect.Concurrent
import cats.effect.Timer
import cats.implicits._
import com.amazonaws.services.logs.model.DescribeLogGroupsRequest
import com.amazonaws.services.logs.model.DescribeLogStreamsRequest
import com.amazonaws.services.logs.model.FilterLogEventsRequest
import com.amazonaws.services.logs.model.FilteredLogEvent
import com.amazonaws.services.logs.model.GetLogEventsRequest
import com.amazonaws.services.logs.model.LogGroup
import com.amazonaws.services.logs.model.LogStream
import com.amazonaws.services.logs.model.OrderBy
import com.amazonaws.services.logs.model.OutputLogEvent
import io.jobial.sprint.util.CatsUtils

import java.lang.System.currentTimeMillis
import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

trait CloudWatchLogsClient[F[_]] extends AwsClient[F] with CatsUtils[F] {

  def describeLogGroups(limit: Int = 1000, nextToken: Option[String] = None)(implicit awsContext: AwsContext, concurrent: Concurrent[F]): F[List[LogGroup]] =
    for {
      r <- fromJavaFuture(awsContext.logs.describeLogGroupsAsync {
        val request = new DescribeLogGroupsRequest()
        nextToken.map(request.withNextToken).getOrElse(request)
      })
      rest <- Option(r.getNextToken) match {
        case Some(token) =>
          val remaining = limit - r.getLogGroups.size
          if (remaining > 0)
            describeLogGroups(remaining, Some(token))
          else
            pure(List())
        case None =>
          pure(List())
      }
    } yield r.getLogGroups.asScala.take(limit).toList ++ rest

  def describeLogStreams(logGroup: String, limit: Int = 1000, nextToken: Option[String] = None)(implicit awsContext: AwsContext, concurrent: Concurrent[F]): F[List[LogStream]] =
    for {
      r <- fromJavaFuture(awsContext.logs.describeLogStreamsAsync {
        val request = new DescribeLogStreamsRequest().withLogGroupName(logGroup)
          .withOrderBy(OrderBy.LastEventTime).withDescending(true)
        nextToken.map(request.withNextToken).getOrElse(request)
      })
      rest <- Option(r.getNextToken) match {
        case Some(token) =>
          val remaining = limit - r.getLogStreams.size
          if (remaining > 0)
            describeLogStreams(logGroup, remaining, Some(token))
          else
            pure(List())
        case None =>
          pure(List())
      }
    } yield r.getLogStreams.asScala.take(limit).toList ++ rest
    
  def getLogEvents(logGroup: String, logStream: String, startTime: Long, endTime: Long)(implicit awsContext: AwsContext, concurrent: Concurrent[F]): F[Vector[OutputLogEvent]] =
    fromJavaFuture(awsContext.logs.getLogEventsAsync {
      new GetLogEventsRequest().withLogGroupName(logGroup)
        .withLogStreamName(logStream)
        .withStartTime(startTime)
        .withEndTime(endTime)
    }).map(_.getEvents.asScala.toVector)

  def filterLogEvents(logGroup: String, startTime: Long, endTime: Long, filterPattern: Option[String] = None, limit: Int = 10000, nextToken: Option[String] = None)(implicit awsContext: AwsContext, concurrent: Concurrent[F]): F[Vector[FilteredLogEvent]] =
    for {
      r <- fromJavaFuture(awsContext.logs.filterLogEventsAsync {
        val request = new FilterLogEventsRequest()
          .withLogGroupName(logGroup)
          .withStartTime(startTime)
          .withEndTime(endTime)
        val request1 = filterPattern.map(request.withFilterPattern).getOrElse(request)
        nextToken.map(request1.withNextToken).getOrElse(request1)
      })
      rest <- Option(r.getNextToken) match {
        case Some(token) =>
          val remaining = limit - r.getEvents.size
          if (remaining > 0)
            filterLogEvents(logGroup, startTime, endTime, filterPattern, remaining, Some(token))
          else
            pure(Vector())
        case None =>
          pure(Vector())
      }
    } yield r.getEvents.asScala.take(limit).toVector ++ rest

  def watchLogEvents[T](group: String, from: Long, filterPattern: Option[String] = None, delay: FiniteDuration = 2.seconds, seen: Set[String] = Set())(f: Vector[FilteredLogEvent] => F[T])(implicit awsContext: AwsContext, concurrent: Concurrent[F], timer: Timer[F]): F[Unit] =
    for {
      events <- filterLogEvents(group, from, currentTimeMillis, filterPattern)
      t = events.lastOption.map(_.getTimestamp.toLong).getOrElse(from)
      events <- pure(events.filterNot(e => seen.contains(e.getEventId)))
      r <- f(events) >> sleep(delay) >> watchLogEvents(group, t, filterPattern, delay, seen ++ events.map(_.getEventId))(f)
    } yield r
  
}  

