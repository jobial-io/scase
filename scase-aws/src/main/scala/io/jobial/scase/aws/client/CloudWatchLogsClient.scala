package io.jobial.scase.aws.client

import cats.Parallel
import cats.effect.Concurrent
import cats.effect.Timer
import cats.implicits._
import com.amazonaws.services.logs.model.DescribeLogGroupsRequest
import com.amazonaws.services.logs.model.DescribeLogStreamsRequest
import com.amazonaws.services.logs.model.FilterLogEventsRequest
import com.amazonaws.services.logs.model.FilteredLogEvent
import com.amazonaws.services.logs.model.GetLogEventsRequest
import com.amazonaws.services.logs.model.ListTagsForResourceRequest
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

  def describeLogGroups(limit: Int = 100)(implicit awsContext: AwsContext, concurrent: Concurrent[F]) =
    getPaginatedResult { nextToken =>
      fromJavaFuture(awsContext.logs.describeLogGroupsAsync {
        val request = new DescribeLogGroupsRequest()
        nextToken.map(request.withNextToken).getOrElse(request)
      })
    }(_.getLogGroups, _.getNextToken, limit)
  
  def describeLogStreams(logGroup: String, limit: Int = 1000, nextToken: Option[String] = None)(implicit awsContext: AwsContext, concurrent: Concurrent[F]) =
    getPaginatedResult { nextToken =>
      fromJavaFuture(awsContext.logs.describeLogStreamsAsync {
        val request = new DescribeLogStreamsRequest().withLogGroupName(logGroup)
          .withOrderBy(OrderBy.LastEventTime).withDescending(true)
        nextToken.map(request.withNextToken).getOrElse(request)
      })
    }(_.getLogStreams, _.getNextToken, limit)
  
  def listTagsForResource(groupArn: String)(implicit awsContext: AwsContext, concurrent: Concurrent[F]) =
    fromJavaFuture(awsContext.logs.listTagsForResourceAsync {
      new ListTagsForResourceRequest().withResourceArn(groupArn)
    })

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

  def forLogEventSequences[T](
    group: String, stream: Option[String],
    from: Long, to: Option[Long] = None, filterPattern: Option[String] = None,
    delay: FiniteDuration = 2.seconds, seen: Set[String] = Set()
  )(f: Vector[FilteredLogEvent] => F[T])(implicit awsContext: AwsContext, concurrent: Concurrent[F], timer: Timer[F]): F[Unit] =
    for {
      events <- filterLogEvents(group, from, currentTimeMillis, filterPattern)
      t = events.lastOption.map(_.getTimestamp.toLong).getOrElse(from)
      events <- pure(events.filterNot(e => seen.contains(e.getEventId))
        .filter(e => to.map(e.getTimestamp < _).getOrElse(true))
        .filter(e => stream.map(e.getLogStreamName.contains).getOrElse(true))
      )
      r <- f(events) >>
        sleep(delay) >>
        whenA(to.map(_ > t).getOrElse(true))(forLogEventSequences(group, stream, t, to, filterPattern, delay, seen ++ events.map(_.getEventId))(f))
    } yield r

  def forLogEvents[T](
    group: String, stream: Option[String],
    from: Long, to: Option[Long], filterPattern: Option[String] = None,
    delay: FiniteDuration = 2.seconds, seen: Set[String] = Set()
  )(f: FilteredLogEvent => F[T])(implicit awsContext: AwsContext, concurrent: Concurrent[F], timer: Timer[F]): F[Unit] =
    forLogEventSequences(group, stream, from, to, filterPattern, delay, seen)(_.map(f).sequence)

  def streams(from: Long, groupLimit: Int = 1000, streamLimit: Int = 10)(implicit awsContext: AwsContext, concurrent: Concurrent[F], parallel: Parallel[F]) =
    for {
      groups <- describeLogGroups(groupLimit)
      groupsAndStreams <- groups.map { g =>
        describeLogStreams(g.getLogGroupName, streamLimit).map(s => g.getLogGroupName -> s.take(1))
      }.parSequence
      r = for {
        (group, streams) <- groupsAndStreams if streams.headOption.flatMap(s => Option(s.getLastEventTimestamp).map(_ > from)).getOrElse(false)
      } yield {

        val streamsByPrefix = streams.groupBy { s =>
          val idx = s.getLogStreamName.lastIndexOf('/')
          s.getLogStreamName.substring(0, if (idx > 0) idx else s.getLogStreamName.size)
        }
        group -> streamsByPrefix
      }
    } yield r

  def forGroupOrStream[T](groupOrStream: String, stream: Option[String], from: Long, to: Option[Long] = None)(f: (String, Option[String]) => F[T])
    (implicit awsContext: AwsContext, concurrent: Concurrent[F], parallel: Parallel[F]) = {
    val streamFilter = stream match {
      case Some(stream) =>
        stream
      case None =>
        groupOrStream
    }

    for {
      streams <- streams(from)
      streamToGroup <- pure(
        for {
          (group, streamsByPrefix) <- streams
          (prefix, streams) <- streamsByPrefix
          stream <- streams
        } yield stream -> group
      )
      matchingStreams = streamToGroup.filter(_._1.getLogStreamName.contains(streamFilter))
      matchingGroups = streamToGroup.map(_._2).filter(_.contains(groupOrStream)).distinct
      _ <- (matchingStreams, matchingGroups) match {
        case (List((stream, group)), _) =>
          f(group, Some(stream.getLogStreamName))
        case (List(), List(group)) =>
          f(group, None)
        case _ =>
          printLn("Cannot identify stream or group")
      }
    } yield (matchingGroups, matchingStreams)
  }
}  
