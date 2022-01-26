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
package io.jobial.condense

import cats.effect.{ContextShift, IO}
import com.monsanto.arch.cloudformation.model.Template
import io.jobial.scase.aws.client.AwsContext

import scala.concurrent.ExecutionContext

trait CloudformationStack
  extends CloudformationSupport {

  def template(implicit context: StackContext): IO[Template]

  def onCreate(implicit context: StackContext): IO[StackContext] = IO(context)

  def onDelete(implicit context: StackContext): IO[StackContext] = IO(context)

  def onUpdate(implicit context: StackContext): IO[StackContext] = IO(context)

  implicit val awsContext = AwsContext()

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
}
