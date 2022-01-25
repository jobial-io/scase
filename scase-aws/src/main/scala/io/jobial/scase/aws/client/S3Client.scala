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

import cats.effect.IO

import java.io.ByteArrayInputStream
import java.lang.Thread.sleep
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.util.IOUtils

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import collection.JavaConverters._


trait S3Client extends AwsClient {

  lazy val s3 = buildAwsClient[AmazonS3ClientBuilder, AmazonS3](AmazonS3ClientBuilder.standard)

  def s3PutText(bucketName: String, key: String, data: String, storageClass: StorageClass = StorageClass.IntelligentTiering) =
    s3PutObject(bucketName, key, data.getBytes("utf-8"), storageClass)

  def s3PutObject(bucketName: String, key: String, data: Array[Byte], storageClass: StorageClass = StorageClass.IntelligentTiering) = IO {
    val request = new PutObjectRequest(bucketName, key, new ByteArrayInputStream(data), new ObjectMetadata).withStorageClass(storageClass)
    s3.putObject(request)
  }

  def s3GetObject(bucketName: String, key: String) = IO {
    val request = new GetObjectRequest(bucketName, key)
    s3.getObject(request)
  }

  def s3GetText(bucketName: String, key: String) =
    for {
      o <- s3GetObject(bucketName, key)
    } yield IOUtils.toString(o.getObjectContent)

  def s3GetObjectIfExists(bucketName: String, key: String) =
    IO(s3.getObject(new GetObjectRequest(bucketName, key))).map(Some(_)).handleErrorWith {
      case t: AmazonServiceException =>
        if (t.getErrorCode == "NoSuchKey") IO(None)
        else throw t
      case t: Throwable =>
        throw t
    }

  def s3GetTextIfExists(bucketName: String, key: String) =
    for {
      r <- s3GetObjectIfExists(bucketName, key)
    } yield r.map(o => IOUtils.toString(o.getObjectContent))

  def s3DeleteObject(bucketName: String, key: String) = IO {
    val request = new DeleteObjectRequest(bucketName, key)
    s3.deleteObject(request)
  }

  def s3ListAllObjects(s3Path: String): Seq[S3ObjectSummary] = {
    val idx = s3Path.indexOf('/')
    s3ListAllObjects(s3Path.substring(0, idx), s3Path.substring(idx + 1))
  }

  def s3ListAllObjects(bucketName: String, prefix: String, maxCount: Option[Int] = None) = {
    def listRemaining(r: ObjectListing): List[S3ObjectSummary] = {
      if (r.isTruncated) {
        val l = s3.listNextBatchOfObjects(r)
        val result = l.getObjectSummaries.iterator.asScala.toList
        if (maxCount.map(_ < result.size).getOrElse(false))
          result
        else
          result ++ listRemaining(l)
      } else List()
    }

    var l = s3.listObjects(bucketName, prefix)

    l.getObjectSummaries.iterator.asScala.toList ++ listRemaining(l)
  }

  def waitForObjectExists(bucketName: String, key: String, repeat: Int = 10): Boolean =
    if (repeat > 0) {
      if (s3.doesObjectExist(bucketName, key))
        true
      else {
        sleep(10000)
        waitForObjectExists(bucketName, key, repeat - 1)
      }
    } else false

  def httpsUrl(bucketName: String, key: String) =
    s"https://s3-${awsContext.region.getOrElse("eu-west-1")}.amazonaws.com/$bucketName/$key"

  def s3CreateBucket(bucketName: String, region: String) = IO {
    val request = new CreateBucketRequest(bucketName, region)
    s3.createBucket(request)
  }
}
