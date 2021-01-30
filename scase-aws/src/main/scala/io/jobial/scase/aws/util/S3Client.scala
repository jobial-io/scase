package io.jobial.scase.aws.util

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

case class AwsContext(
  region: String,
  credentials: Option[AWSCredentials] = None,
  sqsExtendedS3BucketName: Option[String] = None
)

trait S3Client extends AwsClient {

  lazy val s3 = buildAwsClient[AmazonS3ClientBuilder, AmazonS3](AmazonS3ClientBuilder.standard)

  def s3PutText(bucketName: String, key: String, data: String, storageClass: StorageClass = StorageClass.IntelligentTiering): PutObjectResult =
    s3PutObject(bucketName, key, data.getBytes("utf-8"), storageClass)

  def s3PutObject(bucketName: String, key: String, data: Array[Byte], storageClass: StorageClass = StorageClass.IntelligentTiering): PutObjectResult = {
    val request = new PutObjectRequest(bucketName, key, new ByteArrayInputStream(data), new ObjectMetadata).withStorageClass(storageClass)
    s3.putObject(request)
  }

  def s3GetObject(bucketName: String, key: String) = {
    val request = new GetObjectRequest(bucketName, key)
    s3.getObject(request)
  }

  def s3GetText(bucketName: String, key: String) =
    IOUtils.toString(s3GetObject(bucketName, key).getObjectContent)

  def s3GetObjectIfExists(bucketName: String, key: String) = {
    val request = new GetObjectRequest(bucketName, key)
    Try(s3.getObject(request)) match {
      case Failure(t: AmazonServiceException) =>
        if (t.getErrorCode == "NoSuchKey") None
        else throw t
      case Failure(t) =>
        throw t
      case Success(r) =>
        Some(r)
    }
  }

  def s3GetTextIfExists(bucketName: String, key: String) =
    s3GetObjectIfExists(bucketName, key).map(o => IOUtils.toString(o.getObjectContent))

  def s3DeleteObject(bucketName: String, key: String) = {
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
    s"https://s3-${awsContext.region}.amazonaws.com/$bucketName/$key"
}
