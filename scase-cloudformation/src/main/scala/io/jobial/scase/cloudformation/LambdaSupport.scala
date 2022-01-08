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
package io.jobial.scase.cloudformation

import cats.effect.IO
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.monsanto.arch.cloudformation.model.{ConditionRef, Parameter, Template, Token, `Fn::GetAtt`}
import com.monsanto.arch.cloudformation.model.resource.{Code, DeletionPolicy, Java8, Resource, `AWS::Lambda::Function`}
import io.jobial.scase.aws.lambda.{LambdaRequestHandler, LambdaRequestResponseServiceConfiguration, LambdaScheduledRequestHandler}
import spray.json._

import scala.concurrent.duration.{Duration, DurationInt}
import scala.reflect.ClassTag

trait LambdaSupport {
  this: DefaultJsonProtocol =>

  def lambda[REQ, RESP](config: LambdaRequestResponseServiceConfiguration[REQ, RESP]) = {

    object LambdaBuilder {
      def apply[T <: LambdaRequestHandler[IO, REQ, RESP] : ClassTag](
        // using a higher default timeout because the scala library can be slow to load...
        s3Bucket: String,
        s3Key: String,
        timeout: Option[Duration] = Some(10.seconds),
        moduleVersion: String = "master",
        memorySize: Option[Int] = None,
        policies: Seq[JsObject] = Seq()
      ) = lambda[T](
        s3Bucket,
        s3Key,
        timeout,
        memorySize,
        policies
      )

      //      def schedule[T <: LambdaScheduledRequestHandler[REQ, RESP] : ClassTag](
      //        schedule: String,
      //        scheduleEnabled: Boolean = true,
      //        // using a higher default timeout because the scala library can be slow to load...
      //        timeout: Option[Duration] = Some(10 seconds),
      //        moduleVersion: String = "master",
      //        lambdaCodeS3Path: String = defaultLambdaCodeS3Path,
      //        memorySize: Option[Int] = None,
      //        policies: Seq[JsObject] = Seq()
      //      ) = lambda[T](
      //        timeout,
      //        moduleVersion,
      //        lambdaCodeS3Path,
      //        memorySize,
      //        policies,
      //        schedule,
      //        scheduleEnabled
      //      )
    }

    LambdaBuilder
  }

  def uncapitalize(s: String) = s.charAt(0).toLower + s.substring(1)

  def templateFromResources(resources: Resource[_]*) = Template.EMPTY.copy(Resources = resources)

  implicit def resourceToTemplate(r: Resource[_]) = templateFromResources(r)

  implicit def optionalResourceToTemplate(r: Option[Resource[_]]) = templateFromResources(r.toSeq: _*)

  implicit def optionalTemplateToTemplate(r: Option[Template]) = r.getOrElse(Template.EMPTY)

  def templateFromParameters(parameters: Parameter*) = Template.EMPTY.copy(Parameters = parameters)

  implicit def parameterToTemplate(p: Parameter) = templateFromParameters(p)

  implicit def stringToJsObject(s: String) = try {
    s.parseJson.asJsObject
  } catch {
    case t: Throwable =>
      println(s)
      throw t
  }

  def lambda[T <: RequestStreamHandler : ClassTag](
    // using a higher default timeout because the scala library can be slow to load...
    s3Bucket: String,
    s3Key: String,
    timeout: Option[Duration] = Some(10.seconds),
    memorySize: Option[Int] = None,
    policies: Seq[JsObject] = Seq(),
    schedule: Option[String] = None,
    scheduleEnabled: Boolean = true,
    scheduleDescription: Option[String] = None
  ) = {
    val c = implicitly[ClassTag[T]].runtimeClass
    val name = uncapitalize(c.getSimpleName)
    val roleName = s"lambdaRole${name.capitalize}"

    (for {
      schedule <- schedule
    } yield
      (GenericResource(
        "AWS::Events::Rule",
        s"${name}ScheduleRule",
        s"""{
            "Description": "${scheduleDescription.getOrElse("")}",
            "Name": {
              "Fn::Sub": "$${stackLogicalName}-${name}ScheduleRule"
            },
            "ScheduleExpression": "$schedule",
            "State": "${if (scheduleEnabled) "ENABLED" else "DISABLED"}",
            "Targets": [
              {
                "Id": {
                  "Ref": "${name}"
                },
                "Arn": {
                  "Fn::GetAtt": [
                    "${name}",
                    "Arn"
                  ]
                }
              }
            ]
        }""") ++
        GenericResource(
          "AWS::Lambda::Permission",
          s"${name}LambdaPermission",
          s"""{
            "FunctionName": {
                "Fn::GetAtt": [
                  "${name}",
                  "Arn"
                ]
            },
            "Action": "lambda:InvokeFunction",
            "Principal": "events.amazonaws.com",
            "SourceArn": {
              "Fn::GetAtt": [
                "${name}ScheduleRule",
                "Arn"
              ]
            }
        }"""))) ++
      lambdaRole(roleName, policies) ++
      `AWS::Lambda::Function`(
        name = name,
        Code = Code(
          S3Bucket = Some(s3Bucket),
          S3Key = Some(s3Key)
        ),
        Handler = c.getName,
        Role = `Fn::GetAtt`(Seq(roleName, "Arn")),
        Runtime = Java8,
        Timeout = timeout.map(_.toSeconds.toInt),
        MemorySize = memorySize.map(i => i: Token[Int])
      )
  }

  def lambdaRole(name: String, policies: Seq[JsObject]) = GenericResource(
    name = name,
    `Type` = "AWS::IAM::Role",
    Properties =
      s"""{
        "AssumeRolePolicyDocument": {
          "Statement": [
            {
              "Action": [
                "sts:AssumeRole"
              ],
              "Effect": "Allow",
              "Principal": {
                "Service": [
                  "lambda.amazonaws.com"
                ]
              }
            }
          ],
          "Version": "2012-10-17"
        },
        "Path": "/",
        "Policies": [
          {
            "PolicyName": "root",
            "PolicyDocument": {
              "Version": "2012-10-17",
              "Statement": [{ "Effect": "Allow", "Action": ["logs:*"], "Resource": "arn:aws:logs:*:*:*" }]
            }
          }${policies.headOption.map(_ => ",").getOrElse("") + policies.map(_.toString).mkString(",\n")}
        ]        
    }"""
  )

  implicit def genericResourceFormat = new JsonFormat[GenericResource] {
    override def write(obj: GenericResource) = obj.Properties.toJson

    override def read(json: JsValue) = ???
  }

  case class GenericResource(
    Type: String,
    name: String,
    Properties: JsObject = JsObject(),
    override val Condition: Option[ConditionRef] = None,
    override val DependsOn: Option[Seq[String]] = None,
    override val DeletionPolicy: Option[DeletionPolicy] = None
  ) extends Resource[GenericResource] {

    @transient override val ResourceType = Type

    def when(newCondition: Option[ConditionRef] = Condition) = this
  }

}
