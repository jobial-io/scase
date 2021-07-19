package io.jobial.scase.cloudformation

import cats.effect.IO
import com.monsanto.arch.cloudformation.model.Template
import io.jobial.scase.aws.util.S3Client
import spray.json.DefaultJsonProtocol


trait CloudformationStack
  extends CloudformationSupport {

  def template(implicit context: StackContext): IO[Template]

  def onCreate(implicit context: StackContext): IO[StackContext] = IO(context)

  def onDelete(implicit context: StackContext): IO[StackContext] = IO(context)

  def onUpdate(implicit context: StackContext): IO[StackContext] = IO(context)
}
