package io.jobial.scase.aws.client

import cats.effect.IO
import io.jobial.scase.core.test.ScaseTestHelper
import org.scalatest.flatspec.AsyncFlatSpec
import implicits._

class S3ClientTest extends AsyncFlatSpec with S3Client[IO] with ScaseTestHelper {
  lazy val testBucket = if (onGithub) "jobial-ci" else "cloudtemp-build"

  lazy val testPrefix = "test/scase"

  "s3 client" should "work" in {
    val key = s"${testPrefix}/S3ClientTest/text"
    val value = "text"
    for {
      _ <- s3PutText(testBucket, key, value)
      _ <- s3WaitForObjectExists(testBucket, key)
      l <- s3ListAllObjects(s"$testBucket/$testPrefix")
      r <- s3GetText(testBucket, key)
      _ <- s3DeleteObject(testBucket, key)
    } yield {
      assert(l.map(_.getKey).contains(key))
      assert(r === value)
    }
  }
}
