/*
 * Copyright (c) 2020 Jobial OÜ. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package io.jobial.scase.aws.client

import cats.effect.Concurrent
import cats.effect.Timer
import cats.implicits._
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.model._
import com.amazonaws.util.IOUtils
import io.jobial.sprint.process.ProcessContext
import io.jobial.sprint.process.ProcessManagement

import java.io.ByteArrayInputStream
import scala.collection.JavaConverters._
import scala.concurrent.duration._

trait S3Client[F[_]] extends AwsClient[F] with ProcessManagement[F] {

  def s3PutText(bucketName: String, key: String, data: String, storageClass: StorageClass = StorageClass.IntelligentTiering)(implicit context: AwsContext = AwsContext(), concurrent: Concurrent[F]) =
    s3PutObject(bucketName, key, data.getBytes("utf-8"), storageClass)

  def s3PutObject(bucketName: String, key: String, data: Array[Byte], storageClass: StorageClass = StorageClass.IntelligentTiering)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    val request = new PutObjectRequest(bucketName, key, new ByteArrayInputStream(data), new ObjectMetadata).withStorageClass(storageClass)
    context.s3.putObject(request)
  }

  def s3GetObject(bucketName: String, key: String)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    val request = new GetObjectRequest(bucketName, key)
    context.s3.getObject(request)
  }

  def s3Exists(bucketName: String, key: String)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    context.s3.doesObjectExist(bucketName, key)
  }

  def s3GetText(bucketName: String, key: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      o <- s3GetObject(bucketName, key)
    } yield IOUtils.toString(o.getObjectContent)

  def s3GetObjectIfExists(bucketName: String, key: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    delay(context.s3.getObject(new GetObjectRequest(bucketName, key))).map(Option(_)).handleErrorWith {
      case t: AmazonServiceException =>
        if (t.getErrorCode == "NoSuchKey") pure(None)
        else throw t
      case t: Throwable =>
        throw t
    }

  def s3GetTextIfExists(bucketName: String, key: String)(implicit context: AwsContext, concurrent: Concurrent[F]) =
    for {
      r <- s3GetObjectIfExists(bucketName, key)
    } yield r.map(o => IOUtils.toString(o.getObjectContent))

  def s3DeleteObject(bucketName: String, key: String)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    val request = new DeleteObjectRequest(bucketName, key)
    context.s3.deleteObject(request)
  }

  def s3ListAllObjects(s3Path: String)(implicit context: AwsContext, concurrent: Concurrent[F]): F[List[S3ObjectSummary]] = {
    val idx = s3Path.indexOf('/')
    s3ListAllObjects(s3Path.substring(0, idx), s3Path.substring(idx + 1))
  }

  def s3ListAllObjects(bucketName: String, prefix: String, maxCount: Option[Int] = None)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    def listRemaining(r: ObjectListing): List[S3ObjectSummary] = {
      if (r.isTruncated) {
        val l = context.s3.listNextBatchOfObjects(r)
        val result = l.getObjectSummaries.iterator.asScala.toList
        if (maxCount.map(_ < result.size).getOrElse(false))
          result
        else
          result ++ listRemaining(l)
      } else List()
    }

    val l = context.s3.listObjects(bucketName, prefix)

    l.getObjectSummaries.iterator.asScala.toList ++ listRemaining(l)
  }

  def s3WaitForObjectExists(bucketName: String, key: String, repeat: Int = 10)(implicit context: AwsContext, concurrent: Concurrent[F], timer: Timer[F]): F[Boolean] =
    if (repeat > 0) for {
      exists <- s3Exists(bucketName, key)
      r <- if (exists)
        pure(true)
      else for {
        _ <- sleep(5.seconds)
        r <- s3WaitForObjectExists(bucketName, key, repeat - 1)
      } yield r
    } yield r
    else pure(false)

  def httpsUrl(bucketName: String, key: String)(implicit context: AwsContext) =
    s"https://s3-${context.region.getOrElse("eu-west-1")}.amazonaws.com/$bucketName/$key"

  def s3CreateBucket(bucketName: String, region: String)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    val request = new CreateBucketRequest(bucketName, region)
    context.s3.createBucket(request)
  }

  def s3Sync(from: String, to: String, opts: List[String] = List())(implicit processContext: ProcessContext, concurrent: Concurrent[F], timer: Timer[F]) =
    runProcessAndWait(List("aws", "s3", "sync") ++ opts ++ List(from, to))

  def setObjectTagging(bucketName: String, key: String, tags: List[(String, String)])(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    context.s3.setObjectTagging(new SetObjectTaggingRequest(bucketName, key, new ObjectTagging(
      tags.map(t => new Tag(t._1, t._2)).asJava
    )))
  }

  def getObjectTagging(bucketName: String, key: String)(implicit context: AwsContext, concurrent: Concurrent[F]) = delay {
    context.s3.getObjectTagging(new GetObjectTaggingRequest(bucketName, key))
  }

  implicit val getObjectTaggingResultTagged = new Tagged[GetObjectTaggingResult] {
    def tags(tagged: GetObjectTaggingResult) = tagged.getTagSet.asScala.toList.map(t => io.jobial.scase.aws.client.Tag(t.getKey, t.getValue))
  }

}
