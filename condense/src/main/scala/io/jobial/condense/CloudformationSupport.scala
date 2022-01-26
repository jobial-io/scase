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
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.monsanto.arch.cloudformation.model._
import com.monsanto.arch.cloudformation.model.Token._
import com.monsanto.arch.cloudformation.model.resource._
import com.monsanto.arch.cloudformation.model.resource.`AWS::EC2::Volume`._
import com.monsanto.arch.cloudformation.model.simple.Builders._
import io.jobial.scase.aws.client.{ConfigurationUtils, S3Client, StsClient}
import io.jobial.scase.aws.lambda.{LambdaRequestHandler, LambdaRequestResponseServiceConfiguration}
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.logging.Logging
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import spray.json._

import scala.concurrent.duration.{Duration, _}
import scala.reflect.ClassTag

trait CloudformationSupport extends ConfigurationUtils with DefaultJsonProtocol with S3Client with StsClient with Logging {

  case class CloudformationExpression(value: JsValue) {
    override def toString = value.prettyPrint
  }

  implicit def stringToJsObject(s: String) = try {
    s.parseJson.asJsObject
  } catch {
    case t: Throwable =>
      println(s)
      throw t
  }

  implicit def stringToCloudformationExpression(s: String) = try {
    CloudformationExpression(
      if (s.trim.startsWith("{") || s.trim.startsWith("\""))
        s.parseJson
      else
        s.toJson
    )
  } catch {
    case t: Throwable =>
      println(s)
      throw t
  }

  implicit def jsValueToCloudformationExpression(v: JsValue) = CloudformationExpression(v)

  implicit def jsValueToString(v: JsValue) = v.prettyPrint

  implicit def parseJsStringToObject[T: JsonFormat](s: String) = s.toJson.convertTo[T]

  implicit def stringToJsValueMap(s: String) = s.toJson.convertTo[Option[Map[String, JsValue]]]

  implicit def genericResourceFormat = new JsonFormat[GenericResource] {
    override def write(obj: GenericResource) = obj.Properties.toJson

    override def read(json: JsValue) = ???
  }

  def templateFromResources(resources: Resource[_]*) = Template.EMPTY.copy(Resources = resources)

  implicit def resourceToTemplate(r: Resource[_]) = templateFromResources(r)

  implicit def optionalResourceToTemplate(r: Option[Resource[_]]) = templateFromResources(r.toSeq: _*)

  implicit def optionalTemplateToTemplate(r: Option[Template]) = r.getOrElse(Template.EMPTY)

  def templateFromParameters(parameters: Parameter*) = Template.EMPTY.copy(Parameters = parameters)

  implicit def parameterToTemplate(p: Parameter) = templateFromParameters(p)

  def templateIf(condition: Boolean, template: => Template) =
    if (condition)
      template
    else
      Template.EMPTY

  def stringParameter(name: String, description: String, default: Option[String] = None) = StringParameter(
    name,
    description
  ).copy(Default = default)

  implicit def mapToTags(tags: Map[String, String]) = tags.map { case (k, v) => AmazonTag(k, v) }.toSeq

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

  def networkInterface(
    name: String,
    description: String,
    securityGroup: Resource[_],
    subnet: `AWS::EC2::Subnet`,
    tags: Map[String, String]
  ) = GenericResource(
    "AWS::EC2::NetworkInterface",
    name,
    s"""{
          "Description": "$description",
          "GroupSet": [
              {
                "Ref": "${securityGroup.name}"
              }
          ],
          "SubnetId": {
              "Ref": "${subnet.name}"
          },
          "Tags": [
              ${
      tags.map { case (k, v) =>
        s"""{
          "Key": "$k",
          "Value": "$v"
        }"""
      }.mkString(",")
    }
          ]
    }"""
  )

  lazy val ecsInstanceUserData =
    IOUtils.toString(getClass.getResourceAsStream("/cloudtemp/aws/ecs-instance-user-data.sh"), "utf-8")

  def accountId: IO[String] = ???

  def instanceRole(name: String) =
    for {
      accountId <- accountId
    } yield
      GenericResource(
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
                  "ec2.amazonaws.com"
                ]
              }
            }
          ],
          "Version": "2012-10-17"
        },
        "ManagedPolicyArns": [
          "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role"
        ],
        "Path": "/",
        "Policies": [
          {
            "PolicyDocument": {
              "Statement": [
                {
                  "Action": [
                    "ecs:UpdateContainerInstancesState"
                  ],
                  "Effect": "Allow",
                  "Resource": "*"
                }
              ],
              "Version": "2012-10-17"
            },
            "PolicyName": "ecsUpdateContainerInstancesStatePolicy"
          },
          {
            "PolicyDocument": {
              "Statement": [
                {
                  "Action": [
                    "logs:CreateLogGroup",
                    "logs:CreateLogStream",
                    "logs:PutLogEvents",
                    "logs:DescribeLogStreams"
                  ],
                  "Effect": "Allow",
                  "Resource": "arn:aws:logs:*:*:*"
                }
              ],
              "Version": "2012-10-17"
            },
            "PolicyName": "cloudWatchLogsPolicy"
          },
          {
            "PolicyDocument": {
              "Statement": [
                {
                  "Action": [
                    "elasticfilesystem:DescribeMountTargets",
                    "elasticfilesystem:CreateMountTarget",
                    "ec2:DescribeSubnets",
                    "ec2:DescribeNetworkInterfaces",
                    "ec2:CreateNetworkInterface"
                  ],
                  "Effect": "Allow",
                  "Resource": "*"
                }
              ],
              "Version": "2012-10-17"
            },
            "PolicyName": "efsUpdateMountTargets"
          },
          {
            "PolicyDocument": {
              "Statement": [
                {
                  "Action": [
                    "ec2:DetachVolume",
                    "ec2:AttachVolume",
                    "ec2:CreateTags"
                  ],
                  "Effect": "Allow",
                  "Resource": "*"
                }
              ],
              "Version": "2012-10-17"
            },
            "PolicyName": "ec2DetachAttachVolumes"
          },
          {
            "PolicyDocument": {
              "Statement": [
                {
                  "Action": [
                    "ec2:AttachNetworkInterface"
                  ],
                  "Effect": "Allow",
                  "Resource": "*"
                }
              ],
              "Version": "2012-10-17"
            },
            "PolicyName": "ec2AttachNetworkInterface"
          },
          {
            "PolicyDocument": {
              "Statement": [
                {
                  "Action": [
                    "ec2:AssociateAddress",
                    "ec2:DescribeAddresses"
                  ],
                  "Effect": "Allow",
                  "Resource": "*"
                }
              ],
              "Version": "2012-10-17"
            },
            "PolicyName": "ec2AssociateAddress"
          },
          {
            "PolicyDocument": {
              "Statement": [
                {
                  "Action": [
                    "s3:GetObject",
                    "s3:GetObjectVersion",
                    "s3:ListBucket"
                  ],
                  "Effect": "Allow",
                  "Resource": [
                    "arn:aws:s3:::cloudtemp-prod",
                    "arn:aws:s3:::cloudtemp-prod/config/*"
                  ]
                }
              ],
              "Version": "2012-10-17"
            },
            "PolicyName": "AllowReadCloudtempConfigInCloudtempBucket"
          },
          {
            "PolicyDocument": {
              "Statement": [
                {
                  "Action": [
                    "ssm:GetParameters",
                    "ssm:GetParameter"
                  ],
                  "Effect": "Allow",
                  "Resource": [
                    "arn:aws:ssm:${awsContext.region}:$accountId:parameter/config.password"
                  ]
                }
              ],
              "Version": "2012-10-17"
            },
            "PolicyName": "AllowReadConfigPasswordParameter"
          },
          {
            "PolicyDocument": {
              "Statement": [
                {
                  "Action": [
                    "ecs:PutAttributes",
                    "ecs:ListAttributes"
                  ],
                  "Effect": "Allow",
                  "Resource": [
                    "*"
                  ]
                }
              ],
              "Version": "2012-10-17"
            },
            "PolicyName": "AllowReadWriteEcsAttributes"
          },
          {
            "PolicyDocument": {
              "Statement": [
                {
                  "Action": [
                    "route53:ListHostedZones",
                    "route53:ChangeResourceRecordSets"
                  ],
                  "Effect": "Allow",
                  "Resource": [
                    "*"
                  ]
                }
              ],
              "Version": "2012-10-17"
            },
            "PolicyName": "AllowUpdateHostedZoneRecordSet"
          }
        ]
    }"""
      )


  def instanceProfile(name: String) =
    for {
      role <- instanceRole(s"instanceRole${name.capitalize}")
    } yield
      role ++
        GenericResource(
          name = name,
          `Type` = "AWS::IAM::InstanceProfile",
          Properties =
            s"""{
            "Path": "/",
            "Roles": [
                {
                    "Ref": "${role.name}"
                }
            ]
      }"""
        )


  def spotFleetRole(name: String) = GenericResource(
    name = name,
    `Type` = "AWS::IAM::Role",
    Properties =
      """{
        "AssumeRolePolicyDocument": {
          "Statement": [
            {
              "Action": [
                "sts:AssumeRole"
              ],
              "Effect": "Allow",
              "Principal": {
                "Service": [
                  "spotfleet.amazonaws.com"
                ]
              }
            }
          ],
          "Version": "2012-10-17"
        },
        "ManagedPolicyArns": [
          "arn:aws:iam::aws:policy/service-role/AmazonEC2SpotFleetTaggingRole"
        ],
        "Path": "/"
      }"""
  )

  val ecsOptimisedAmiForRegion = Map(
    "eu-west-1" -> "ami-0d9430336a60e81c5"
  )

  def printTags(tags: Map[String, String]) = tags.map { case (k, v) =>
    s"""{
      "Key": "$k",
      "Value": "$v"
    }"""
  }.mkString(",")

  def spotFleet(
    name: String,
    instanceTypes: Seq[String],
    securityGroup: Resource[_],
    subnet: `AWS::EC2::Subnet`,
    keyName: CloudformationExpression,
    userData: String,
    amiId: CloudformationExpression = ecsOptimisedAmiForRegion(awsContext.region.getOrElse(???)),
    tags: Map[String, String] = Map(),
    dependsOn: Seq[String] = Seq()
  ) = {
    val allocationStrategy =
      if (instanceTypes.size > 1) "lowestPrice" // this seems to cause a lot of spontaneous instance terminations
      else "diversified" // this was somehow more stable, but it only works for one instance type obviously

    val instanceProfileName = s"spotFleetInstanceProfile${name.capitalize}"
    val fleetRole = spotFleetRole(s"spotFleetRole${name.capitalize}")

    for {
      profile <- instanceProfile(instanceProfileName)
    } yield
      profile ++ fleetRole ++
        GenericResource(
          "AWS::EC2::SpotFleet",
          name,
          DependsOn = dependsOn,
          Properties =
            s"""{
              "SpotFleetRequestConfigData": {
                  "AllocationStrategy": "$allocationStrategy",
                  "IamFleetRole": {
                      "Fn::GetAtt": [
                          "${fleetRole.name}",
                          "Arn"
                      ]
                  },
                  "LaunchSpecifications": [
                  ${
              instanceTypes.map { instanceType =>
                s"""
                      {
                          "IamInstanceProfile": {
                              "Arn": {
                                  "Fn::GetAtt": [
                                      "$instanceProfileName",
                                      "Arn"
                                  ]
                              }
                          },
                          "ImageId": ${amiId},
                          "InstanceType": "$instanceType",
                          "KeyName": ${keyName},
                          "SecurityGroups": [
                              {
                                  "GroupId": {
                                      "Ref": "${securityGroup.name}"
                                  }
                              }
                          ],
                          "SubnetId": {
                              "Fn::Join": [
                                  ",",
                                  [
                                      {
                                          "Ref": "${subnet.name}"
                                      }
                                  ]
                              ]
                          },
                          "UserData": {
                              "Fn::Base64": {
                                  "Fn::Sub": ${s"$ecsInstanceUserData\n\n$userData".toJson}
                              }
                          }
                          ${
                  tags.headOption.map { _ =>
                    s""",
              "TagSpecifications": [
              {
                "ResourceType": "instance",
                "Tags": [
                ${printTags(tags)}
                ]
              }
              ]
              """
                  }.getOrElse("")
                }
                  }
                  """
              }.mkString(",")
            }
                  ],
                  "TargetCapacity": 1,
                  "ReplaceUnhealthyInstances": true,
                  "TerminateInstancesWithExpiration": false,
                  "Type": "maintain",
                  "SpotPrice": 5.0
              }
        }"""
        )
  }

  def ec2Instance(
    name: String,
    instanceType: String,
    securityGroup: GenericResource,
    subnet: `AWS::EC2::Subnet`,
    keyName: CloudformationExpression,
    userData: String,
    amiId: CloudformationExpression = ecsOptimisedAmiForRegion(awsContext.region.getOrElse(???)),
    tags: Map[String, String] = Map(),
    dependsOn: Seq[String] = Seq()
  ) = {
    val instanceProfileName = s"instanceProfile${name.capitalize}"
    for {
      profile <- instanceProfile(instanceProfileName)
    } yield
      profile ++
        GenericResource(
          "AWS::EC2::Instance",
          name,
          DependsOn = dependsOn,
          Properties =
            s"""{
            "IamInstanceProfile": {
                "Ref": "$instanceProfileName"
             },
            "ImageId": ${amiId},
            "InstanceType": "$instanceType",
            "KeyName": ${keyName},
            "SecurityGroupIds": [
                {
                  "Ref": "${securityGroup.name}"
                }
            ],
            "SubnetId": {
                "Fn::Join": [
                    ",",
                    [
                        {
                            "Ref": "${subnet.name}"
                        }
                    ]
                ]
            },
            "UserData": {
                "Fn::Base64": {
                    "Fn::Sub": ${s"$ecsInstanceUserData\n\n$userData".toJson}
                }
            }
                          ${
              tags.headOption.map { _ =>
                s""",
            "Tags": [
            ${printTags(tags)}
            ]
            """
              }.getOrElse("")
            }
        }"""
        )
  }

  def spotFleetOrInstance(
    name: String,
    instanceTypes: Seq[String],
    securityGroup: GenericResource,
    subnet: `AWS::EC2::Subnet`,
    keyName: CloudformationExpression,
    userData: String,
    amiId: CloudformationExpression = ecsOptimisedAmiForRegion(awsContext.region.getOrElse(???)),
    tags: Map[String, String] = Map(),
    dependsOn: Seq[String] = Seq(),
    useOnDemandInstances: Boolean
  ) =
    if (useOnDemandInstances)
      ec2Instance(
        s"${name}Instance",
        instanceTypes.head,
        securityGroup,
        subnet,
        keyName,
        userData,
        amiId,
        tags,
        dependsOn
      )
    else
      spotFleet(
        s"${name}SpotFleet",
        instanceTypes,
        securityGroup,
        subnet,
        keyName,
        userData,
        amiId,
        tags,
        dependsOn
      )

  val defaultTaskTargetRoleName = "taskTargetRole"

  def taskTargetRole = {
    for {
      accountId <- accountId
    } yield
      GenericResource(
        name = defaultTaskTargetRoleName,
        `Type` = "AWS::IAM::Role",
        Properties =
          s"""{
        "RoleName": {
          "Fn::Sub": "taskTargetRole$${AWS::StackName}"
        },
        "Path": "/",
        "AssumeRolePolicyDocument": {
          "Statement": [{
          "Effect": "Allow",
          "Principal": { 
          "Service": [ "events.amazonaws.com" ]}, 
          "Action": [ "sts:AssumeRole" ] }]
          },
        "Policies": [
          {
            "PolicyName": "ecsTaskPolicy",
            "PolicyDocument": {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Action": [
                    "ecs:RunTask",
                    "ecs:PassTask",
                    "ecs:PutTask"
                  ],
                  "Resource": "*"
                },
                {
                  "Effect": "Allow",
                  "Action": [
                    "iam:PassRole"
                  ],
                  "Resource": "arn:aws:iam::$accountId:role/ecsTaskExecutionRole",
                  "Condition": {
                    "StringLike": {
                      "iam:PassedToService": "ecs-tasks.amazonaws.com"
                    }
                  }
                }
              ]
            }
          }
        ]
      }"""
      )
  }

  def serviceRole = GenericResource(
    "AWS::IAM::Role",
    "serviceRole",
    """{
        "RoleName": {
          "Fn::Sub": "ecs-service-${AWS::StackName}"
        },
        "Path": "/",
        "AssumeRolePolicyDocument": "{\n    \"Statement\": [{\n        \"Effect\": \"Allow\",\n        \"Principal\": { \"Service\": [ \"ecs.amazonaws.com\" ]},\n        \"Action\": [ \"sts:AssumeRole\" ]\n    }]\n}\n",
        "Policies": [
          {
            "PolicyName": "ecsServiceLoadBalancerPolicy",
            "PolicyDocument": {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Action": [
                    "ec2:AuthorizeSecurityGroupIngress",
                    "ec2:Describe*",
                    "elasticloadbalancing:DeregisterInstancesFromLoadBalancer",
                    "elasticloadbalancing:Describe*",
                    "elasticloadbalancing:RegisterInstancesWithLoadBalancer",
                    "elasticloadbalancing:DeregisterTargets",
                    "elasticloadbalancing:DescribeTargetGroups",
                    "elasticloadbalancing:DescribeTargetHealth",
                    "elasticloadbalancing:RegisterTargets"
                  ],
                  "Resource": "*"
                }
              ]
            }
          }
        ]
  }"""
  )

  def taskRole(name: String, policies: Seq[JsObject] = Seq()) = GenericResource(
    "AWS::IAM::Role",
    name,
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
                  "ecs-tasks.amazonaws.com"
                ]
              }
            }
          ],
          "Version": "2012-10-17"
        },
        "ManagedPolicyArns": [
          "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role"
        ],
        "Path": "/",
        "Policies": [
          {
            "PolicyDocument": {
              "Statement": [
                {
                  "Action": [
                    "logs:CreateLogGroup",
                    "logs:CreateLogStream",
                    "logs:PutLogEvents",
                    "logs:DescribeLogStreams"
                  ],
                  "Effect": "Allow",
                  "Resource": "arn:aws:logs:*:*:*"
                }
              ],
              "Version": "2012-10-17"
            },
            "PolicyName": "CloudWatchLogsPolicy"
          },
          {
            "PolicyDocument": {
              "Statement": [
                {
                  "Action": [
                    "ecr:GetAuthorizationToken",
                    "ecr:BatchCheckLayerAvailability",
                    "ecr:GetDownloadUrlForLayer",
                    "ecr:BatchGetImage",
                    "logs:CreateLogStream",
                    "logs:PutLogEvents"
                  ],
                  "Effect": "Allow",
                  "Resource": "*"
                }
              ],
              "Version": "2012-10-17"
            },
            "PolicyName": "TaskExecutionRolePolicy"
          }${policies.headOption.map(_ => ",").getOrElse("") + policies.map(_.toString).mkString(",\n")}
        ]
  }"""
  )

  def policy(name: String, actions: Seq[String], effect: String = "Allow", resource: Seq[String] = Seq("*")): JsObject =
    s"""
{
            "PolicyDocument": {
              "Statement": [
                {
                  "Action": ${actions.toJson},
                  "Effect": ${effect.toJson},
                  "Resource": ${resource.toJson}
                }
              ],
              "Version": "2012-10-17"
            },
            "PolicyName": "$name"
          }
"""

  def ecsTaskDefinition(
    name: String,
    containerName: String,
    logGroupName: String,
    logStreamPrefix: String,
    containerImageRootUrl: String,
    memorySoftLimit: Option[Int],
    memoryHardLimit: Option[Int],
    image: Option[String] = None,
    imageName: Option[String] = None,
    imageTag: Option[String] = None,
    entryPoint: Option[Seq[String]] = None,
    command: Option[Seq[String]] = None,
    placementConstraints: Seq[JsObject] = Seq(),
    portMappings: Map[Int, Int] = Map(),
    volumes: Map[String, String] = Map(),
    mountPoints: Map[String, String] = Map(),
    environment: Map[String, String] = Map(),
    taskRolePolicies: Seq[JsObject] = Seq()
  ) = {
    val actualImageName = imageName.getOrElse(containerName)

    val actualImage = image.getOrElse(s"$containerImageRootUrl/$actualImageName${imageTag.map(t => s":$t").getOrElse("")}")

    val taskRoleName = s"${name}TaskRole"

    taskRole(taskRoleName, taskRolePolicies) ++
      GenericResource(
        "AWS::ECS::TaskDefinition",
        s"${name}TaskDefinition",
        s"""{
              "ContainerDefinitions": [
                  {
                      "Name": "$containerName",
                      "Image": "$actualImage",
                      "PortMappings": [ ${
          (for (m <- portMappings) yield
            s"""{
                          "ContainerPort": ${m._2}
                          ,
                          "HostPort": ${m._1}
                        }""").mkString(",")
        }],
                      "Ulimits": [
                          {
                              "Name": "nofile",
                              "SoftLimit": 100000,
                              "HardLimit": 100000
                          }
                      ],
                      "MountPoints": [${
          mountPoints.map { case (k, v) =>
            JsObject("SourceVolume" -> k.toJson, "ContainerPath" -> v.toJson)
          }.map(_.prettyPrint).mkString(",")
        }],
                      ${memoryHardLimit.map(m => s""""Memory": "$m",""").getOrElse("")}
                      ${memorySoftLimit.map(m => s""""MemoryReservation": "$m",""").getOrElse("")}
                      ${entryPoint.map(e => s""""EntryPoint": ${e.toJson},""").getOrElse("")}
                      ${command.map(c => s""""Command": [ ${c.map { c => s"""{ "Fn::Sub": ${c.toJson} }""" }.mkString(",")} ],""").getOrElse("")}
                      "LogConfiguration": {
                        "LogDriver": "awslogs",
                        "Options": {
                          "awslogs-group": ${logGroupName},
                          "awslogs-region": {
                            "Ref": "AWS::Region"
                          },
                          "awslogs-stream-prefix": {
                            "Fn::Sub": "${logStreamPrefix}"
                          }
                        }
                      },
                      "Environment": [
                      ${
          environment.map { case (k, v) =>
            s"""{
              "Name": "$k"
              ,
              "Value": "$v"
            }"""
          }.mkString(",")
        }
                      ],
                      "Essential": "true"
                  }
              ],
              "Volumes": [${
          volumes.map { case (k, v) =>
            JsObject("Host" -> JsObject("SourcePath" -> k.toJson), "Name" -> v.toJson)
          }.map(_.prettyPrint).mkString(",")
        }],
              "NetworkMode": "bridge",
              "PlacementConstraints": ${placementConstraints.mkString("[", ",", "]")},
              "RequiresCompatibilities": [
                  "EC2"
              ],
              "TaskRoleArn": {
                "Fn::GetAtt": [
                  "$taskRoleName",
                  "Arn"
                ]
              }
        }"""
      )

  }

  def ecsServiceTask(
    name: String,
    memorySoftLimit: Option[Int],
    memoryHardLimit: Option[Int],
    containerImageRootUrl: String,
    serviceName: Option[String] = None,
    image: Option[String] = None,
    imageName: Option[String] = None,
    imageTag: Option[String] = None,
    entryPoint: Option[Seq[String]] = None,
    command: Option[Seq[String]] = None,
    placementConstraints: Seq[JsObject] = Seq(),
    portMappings: Map[Int, Int] = Map(),
    volumes: Map[String, String] = Map(),
    mountPoints: Map[String, String] = Map(),
    environment: Map[String, String] = Map(),
    taskRolePolicies: Seq[JsObject] = Seq(),
    clusterName: String = defaultEcsClusterName,
    logGroupName: String = defaultLogGroupName,
    desiredCount: Int = 1
  ) = {
    val actualServiceName = serviceName.getOrElse(StringUtils.splitByCharacterTypeCamelCase(name).map(_.toLowerCase).mkString("-"))

    val containerName = actualServiceName

    ecsTaskDefinition(
      name,
      containerName,
      defaultLogGroupName,
      logStreamPrefix = s"ecs/$actualServiceName-service/$${$clusterName}",
      containerImageRootUrl,
      memorySoftLimit,
      memoryHardLimit,
      image,
      imageName,
      imageTag,
      entryPoint,
      command,
      placementConstraints,
      portMappings,
      volumes,
      mountPoints,
      environment,
      taskRolePolicies
    ) ++
      GenericResource(
        "AWS::ECS::Service",
        s"${name}Service",
        s"""{
              "Cluster": {
                  "Ref": "$clusterName"
              },
              "DesiredCount": $desiredCount,
              "TaskDefinition": {
                  "Ref": "${name}TaskDefinition"
              },
              "ServiceRegistries": [
              ${
          portMappings.headOption.map { p =>
            s""" {
            "RegistryArn": {
              "Fn::GetAtt": [
              "${name}ServiceDiscoveryService",
              "Arn"
              ]
            },
            "ContainerName": "$containerName",
            "ContainerPort": ${p._2}
          }

        """
          }.getOrElse("")
        }
              ]
        }"""
      ) ++
      GenericResource(
        "AWS::ServiceDiscovery::Service",
        s"${name}ServiceDiscoveryService",
        s"""{
              "Name": "$actualServiceName",
              "DnsConfig": {
                  "NamespaceId": {
                      "Ref": "serviceDiscoveryNamespace"
                  },
                  "DnsRecords": [
                      {
                          "Type": "SRV",
                          "TTL": 300
                      }
                  ]
              },
              "HealthCheckCustomConfig": {
                  "FailureThreshold": 1
              }
        }"""
      )
  }

  def ecsScheduledTask(
    name: String,
    description: String,
    scheduleExpression: String,
    containerImageRootUrl: String,
    ruleName: Option[String] = None,
    memorySoftLimit: Option[Int],
    memoryHardLimit: Option[Int],
    image: Option[String] = None,
    imageName: Option[String] = None,
    imageTag: Option[String] = None,
    entryPoint: Option[Seq[String]] = None,
    command: Option[Seq[String]] = None,
    placementConstraints: Seq[JsObject] = Seq(),
    portMappings: Map[Int, Int] = Map(),
    volumes: Map[String, String] = Map(),
    mountPoints: Map[String, String] = Map(),
    environment: Map[String, String] = Map(),
    taskRolePolicies: Seq[JsObject] = Seq(),
    clusterName: String = defaultEcsClusterName,
    logGroupName: String = defaultLogGroupName,
    scheduleEnabled: Boolean = true
  ) = {

    val containerName = ruleName.getOrElse(StringUtils.splitByCharacterTypeCamelCase(name).map(_.toLowerCase).mkString("-"))

    val actualRuleName = ruleName.getOrElse(name)

    val streamPrefixName = StringUtils.splitByCharacterTypeCamelCase(actualRuleName).map(_.toLowerCase).mkString("-")

    ecsTaskDefinition(
      name,
      containerName,
      defaultLogGroupName,
      logStreamPrefix = s"ecs/$streamPrefixName-scheduled-task/$${$clusterName}",
      containerImageRootUrl,
      memorySoftLimit,
      memoryHardLimit,
      image,
      imageName,
      imageTag,
      entryPoint,
      command,
      placementConstraints,
      portMappings,
      volumes,
      mountPoints,
      environment,
      taskRolePolicies
    ) ++
      GenericResource(
        "AWS::Events::Rule",
        s"${name}ScheduleRule",
        s"""{
            "Description": "$description",
            "Name": {
              "Fn::Sub": "$${stackLogicalName}-${actualRuleName}ScheduleRule"
            },
            "ScheduleExpression": "$scheduleExpression",
            "State": "${if (scheduleEnabled) "ENABLED" else "DISABLED"}",
            "Targets": [
              {
                "Id": "${name}TaskTarget",
                "RoleArn": {
                  "Fn::GetAtt": [
                    "taskTargetRole",
                    "Arn"
                  ]
                },
                "EcsParameters": {
                  "TaskDefinitionArn": {
                    "Ref": "${name}TaskDefinition"
                  },
                  "TaskCount": 1
                },
                "Arn": {
                  "Fn::GetAtt": [
                    "${clusterName}",
                    "Arn"
                  ]
                }
              }
            ]
        }""")
  }

  def ecsCluster(name: String) = GenericResource(
    name = name,
    Type = "AWS::ECS::Cluster"
  )

  def logGroup(name: String, logGroupName: CloudformationExpression, deletionPolicy: DeletionPolicy = DeletionPolicy.Retain) = GenericResource(
    name = name,
    Type = "AWS::Logs::LogGroup",
    DeletionPolicy = Some(deletionPolicy),
    Properties =
      s"""{
        "LogGroupName": ${logGroupName}
}"""
  )

  def defaultEcsClusterName = "ecsCluster"

  def ecsResources(namespaceName: String, vpc: `AWS::EC2::VPC`, serviceDiscoveryNamespaceName: String = "serviceDiscoveryNamespace") =
    for {
      taskTargetRole <- taskTargetRole
    } yield
      ecsCluster(defaultEcsClusterName) ++
        serviceRole ++
        taskTargetRole ++
        serviceDiscoveryNamespaces(
          name = serviceDiscoveryNamespaceName,
          description = "Service discovery namespace for services that require service discovery based on SRV records",
          vpc = vpc,
          namespaceName = namespaceName
        )

  def securityGroupIngress(cidrIp: String, fromPort: Int, toPort: Int, ipProtocol: String): JsObject =
    s"""
    {
        "CidrIp": "$cidrIp",
        "FromPort": $fromPort,
        "IpProtocol": "$ipProtocol",
        "ToPort": $toPort
    }
"""

  def securityGroup(ingresses: Seq[JsObject], vpc: `AWS::EC2::VPC`) = GenericResource(
    "AWS::EC2::SecurityGroup",
    "securityGroup",
    s"""{
                  "GroupDescription": "Spot fleet instance Security Group",
                  "VpcId": {
                      "Ref": "${vpc.name}"
                  },
                  "SecurityGroupIngress": [
                    ${ingresses.mkString(",")}
                  ]
            }"""
  )

  def privateDnsNamespace(name: String, description: String, namespaceName: String, vpc: `AWS::EC2::VPC`) =
    GenericResource(
      "AWS::ServiceDiscovery::PrivateDnsNamespace",
      name,
      s"""{
            "Description": "$description",
            "Vpc": {
                "Ref": "${vpc.name}"
            },
            "Name": "$namespaceName"
      }"""
    )

  def hostedZone(name: String, description: String, namespaceName: String, vpc: `AWS::EC2::VPC`) =
    GenericResource(
      "AWS::Route53::HostedZone",
      name,
      s"""{
        "HostedZoneConfig": {
          "Comment": ${description.toJson}
        },
        "VPCs": [
          {
            "VPCId": {
              "Ref": "${vpc.name}"
            },
            "VPCRegion": {
              "Ref": "AWS::Region"
            }
          }
        ],
        "Name": "$namespaceName"
      }"""
    )

  def serviceDiscoveryNamespaces(name: String, description: String, namespaceName: String, vpc: `AWS::EC2::VPC`) =
    privateDnsNamespace(
      name = name,
      description = "Service discovery namespace for services that require service discovery outside the load balancer",
      vpc = vpc,
      namespaceName = namespaceName
    ) ++
      hostedZone(
        name = s"mapped${name.capitalize}",
        description = "Namespace for mapping SRV records to A records (as SRV records are not supported by most applications at the moment)",
        vpc = vpc,
        namespaceName = s"a_$namespaceName"
      )


  def vpcWithSubnet(
    availabilityZone: Token[String],
    vpcName: String = "vpc",
    cidrBlock: CidrBlock = CidrBlock(10, 0, 0, 0, 16),
    subnetName: String = "publicSubnet1",
    subnetCidrBlock: CidrBlock = CidrBlock(10, 0, 0, 1, 24),
    publicPorts: Seq[Int] = Seq(22, 80, 443),
    privatePorts: Seq[Int] = Seq(9200, 9300, 2049)
  )(f: (`AWS::EC2::VPC`, `AWS::EC2::Subnet`, GenericResource) => Template) = {

    implicit val vpc = `AWS::EC2::VPC`(
      "vpc",
      CidrBlock = cidrBlock,
      Tags = AmazonTag.fromName(vpcName),
      EnableDnsSupport = true,
      EnableDnsHostnames = true
    )
    //.andOutput("vpcId", "VPC Info", `Fn::Sub`(s"$${AWS::StackName}-VPCID"))

    val (internetGatewayResource, gatewayToInternetResource) = withInternetGateway

    val publicRouteTable = withRouteTable("Public", 1)

    val publicRouteTableRoute = publicRouteTable.withRoute(
      visibility = "Public",
      routeTableOrdinal = 1,
      routeOrdinal = 1,
      connectionBobber = InternetGatewayRoute(ResourceRef(internetGatewayResource)),
      dependsOn = Seq("InternetGateway", "GatewayToInternet")
    )

    val gatewayStuff = internetGatewayResource ++
      gatewayToInternetResource ++
      publicRouteTable ++
      publicRouteTableRoute

    implicit val subnet = `AWS::EC2::Subnet`(
      subnetName,
      VpcId = vpc,
      AvailabilityZone = availabilityZone,
      CidrBlock = subnetCidrBlock,
      MapPublicIpOnLaunch = Some(true),
      Tags = AmazonTag.fromName(subnetName)
    )

    val routeTableAssoc = withRouteTableAssoc("Public", 1, publicRouteTable)

    val sourceCidr = "0.0.0.0/0"

    val subnetCidr = cidrFromSubnet(subnet)

    val secGroup = securityGroup(
      publicPorts.map(p => securityGroupIngress(sourceCidr, p, p, "tcp")) ++
        privatePorts.map(p => securityGroupIngress(subnetCidr, p, p, "tcp")),
      vpc)

    vpc ++
      internetGatewayResource ++
      gatewayToInternetResource ++
      publicRouteTable ++
      publicRouteTableRoute ++
      subnet ++
      routeTableAssoc ++
      secGroup ++
      gatewayStuff ++
      f(vpc, subnet, secGroup)
  }

  def cidrFromSubnet(subnet: `AWS::EC2::Subnet`) =
    subnet.CidrBlock.toJson.asInstanceOf[JsString].value

  def templateFromDescription(description: String) = Template(
    AWSTemplateFormatVersion = Some("2010-09-09"),
    Description = Some(description),
    Resources = Seq.empty
  )

  def stack(
    logicalName: String,
    description: String
  ) = templateFromDescription(description) ++
    stringParameter(
      "stackLogicalName",
      s"Logical name of the stack. " +
        s"It can be different from the $${AWS::StackName} value for technical reasons (e.g. if a previous instance of the stack cannot easily be deleted).",
      logicalName
    )

  // TODO: maybe this should be the cluster name instead...
  def defaultLogGroupName: JsObject =
    s"""{
      "Fn::Join" : [ "/", [
        { "Ref" : "stackLogicalName" },
        { "Fn::Select" : [ "2", { "Fn::Split": ["/", {"Ref" : "AWS::StackId"}]  }] }
  ]]
  }"""

  def defaultLogGroup(deletionPolicy: DeletionPolicy = DeletionPolicy.Retain) = logGroup("logGroup", defaultLogGroupName, deletionPolicy)

  def memberOf(expression: String): JsObject =
    s"""
            {
            "Type": "memberOf",
            "Expression": "$expression"
          }
"""

  def addSwap(sizeInMegabytes: Int) = s"\nadd_swap $sizeInMegabytes"

  def associateAddress(address: String) =
    s"\nassociate_address $address"

  def attachInterfaceAndAssociateAddress(interfaceId: String, index: Int, address: String) =
    s"\nattach_interface_and_associate_address $interfaceId $index $address"

  def configureInterfaceForDocker(interfaceUnixName: String) =
    s"\nconfigure_interface_for_docker $interfaceUnixName"

  def redirectPortOnInterface(interfaceUnixName: String, fromPort: Int, toPort: Int) =
    s"\nredirect_port_on_interface $interfaceUnixName $fromPort $toPort"

  def addEcsInstanceAttributes(attributes: (String, String)*) =
    s"""\nadd_ecs_instance_attributes "{${attributes.map { case (k, v) => s"""\\\"$k\\\": \\\"$v\\\"""" }.mkString(",")}}""""

  def addDomainSearch(searchDomain: String) =
    s"\nadd_domain_search $searchDomain"

  def subRef(r: Resource[_]) = s"$${${r.name}}"

  def subRef(p: Parameter) = s"$${${p.name}}"

  def ref(p: Resource[_]) = s"""{ "Ref": "${p.name}" }"""

  def ref(p: Parameter) = s"""{ "Ref": "${p.name}" }"""

  def attachVolume(volumeId: String, device: String, mountPoint: String) =
    s"\nattach_volume $volumeId $device $mountPoint"

  def chmod(options: String, dir: String) =
    s"\nchmod $options $dir"

  def volume(name: String, availabilityZone: String, snapshotId: String, tags: Map[String, String]) =
    gp2Snapshot(name, availabilityZone, snapshotId, tags)

  def volume(name: String, availabilityZone: String, sizeInGB: Int, tags: Map[String, String]) =
    gp2(name, availabilityZone, sizeInGB, tags)

  def updateAliasRecord(hostedZoneName: String, recordName: String, comment: String) =
    s"""\nupdate_alias_record $hostedZoneName $recordName "$comment""""

  def elasticIp(name: String) =
    `AWS::EC2::EIP`(
      name,
      None,
      None,
      None,
      None
    )

  def aliasRecord(name: String, recordName: String, hostedZoneName: String, address: String) =
    `AWS::Route53::RecordSet`.generalRecord(
      name,
      recordName,
      Route53RecordType.A,
      hostedZoneName,
      Seq(`Fn::Sub`(address)),
      "300"
    )

  def policyToAllowReadWriteListOfS3Bucket(bucket: String) =
    policy(
      name = s"AllowReadWriteListOfS3${bucket.split("-").map(_.capitalize).mkString}Bucket",
      actions = Seq(
        "s3:PutObject",
        "s3:GetObject",
        "s3:ListBucket",
        "s3:DeleteObject",
        "s3:HeadBucket",
        "s3:HeadObject"
      ),
      resource = Seq(
        s"arn:aws:s3:::$bucket",
        s"arn:aws:s3:::$bucket/*"
      )
    )

  //  def java[T <: {def main(args: Array[String]): Unit} : ClassTag](main: T, classPath: String = "../lib/*", heapSize: Int)(args: String*) =
  //    Seq("/usr/bin/java", "-cp", classPath) ++ javaHeapOpts(heapSize) ++ javaGcOpts ++
  //      (mainClassName[T] +: args.toSeq)
  //
  def nice(level: Int = 20) =
    Seq("/usr/bin/nice", "-n", "20")

  val instanceTypesWithAtLeast32Gb = Seq("m4.2xlarge", "m4.4xlarge", "m5.2xlarge", "m5.4xlarge")

  val instanceTypesWithAtLeast16Gb = Seq("m4.xlarge", "m5.xlarge") ++ instanceTypesWithAtLeast32Gb

  val instanceTypesWithAtLeast8Gb = Seq("m4.large", "m5.large") ++ instanceTypesWithAtLeast16Gb

  // TODO: this is obviously not very generic, I just left here as an example; currently we don't use alarms
  //  - because it is actually not possible to monitor and terminate spot instances this way from cloudformation
  //  - since the instance id is not known in advance. Because if this, the terminate action must run outside
  //  - the alarm. Now it would be possible to forward the alarm to a queue or topic and do the termination
  //  - by listening, but for now we might as well just do the monitoring there too if we need a separate
  //  - task for the termination anyway.
  def ec2TerminateAlarm(name: String, metricName: String, instanceId: String) = `AWS::CloudWatch::Alarm`(
    name = name,
    ComparisonOperator = `AWS::CloudWatch::Alarm::ComparisonOperator`.GreaterThanThreshold,
    EvaluationPeriods = "3",
    MetricName = metricName,
    Namespace = "Condense",
    Period = "60",
    Statistic = `AWS::CloudWatch::Alarm::Statistic`.Sum,
    Threshold = "2",
    AlarmActions = Some(s"""arn:aws:automate:${awsContext.region}:ec2:terminate"""),
    Dimensions = Some(Seq(`AWS::CloudWatch::Alarm::Dimension`("InstanceId", instanceId))) //Some(Seq(`AWS::CloudWatch::Alarm::Dimension`("Environment", stackLogicalName)))
  )

  private def moduleNameForClass(c: Class[_]) = {
    // Examples:
    // jar:file:/Users/gyorgy/.m2/repository/com/monsanto/arch/cloud-formation-template-generator_2.11/3.9.1/cloud-formation-template-generator_2.11-3.9.1.jar!/com/monsanto/arch/cloudformation/model/Template.class
    // file:/Users/gyorgy/Projects/lambda-example/target/classes/lambda/Hello.class
    // TODO: currently we ignore the groupId of the module
    val resourceUri = c.getClassLoader.getResource(c.getName.replace('.', '/') + ".class").toString
    //    logger.debug(resourceUri)
    val parts = resourceUri.split("/")
    if (resourceUri.startsWith("file:"))
      parts.reverse.dropWhile(_ != "target").drop(1).head
    else
      parts.reverse.dropWhile(!_.endsWith(".jar!")).drop(2).head
  }

  //  def lambda[REQ, RESP](config: LambdaRequestResponseServiceConfiguration[REQ, RESP]) = {
  //
  //    object LambdaBuilder {
  //      def apply[T <: LambdaRequestHandler[IO, REQ, RESP] : ClassTag](
  //        // using a higher default timeout because the scala library can be slow to load...
  //        timeout: Option[Duration] = Some(10.seconds),
  //        moduleVersion: String = "master",
  //        lambdaCodeS3Path: String = defaultLambdaCodeS3Path,
  //        memorySize: Option[Int] = None,
  //        policies: Seq[JsObject] = Seq()
  //      ) = lambda[T](
  //        timeout,
  //        moduleVersion,
  //        lambdaCodeS3Path,
  //        memorySize,
  //        policies
  //      )
  //
  //      def schedule[T <: LambdaScheduledRequestHandler[IO, REQ, RESP] : ClassTag](
  //        schedule: String,
  //        scheduleEnabled: Boolean = true,
  //        // using a higher default timeout because the scala library can be slow to load...
  //        timeout: Option[Duration] = Some(10.seconds),
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
  //    }
  //
  //    LambdaBuilder
  //  }
  //  
  // see: https://stackoverflow.com/questions/41452274/how-to-create-a-new-version-of-a-lambda-function-using-cloudformation
  // TODO: make this generic for any F[_]
  def lambda[T: ClassTag](
    // using a higher default timeout because the scala library can be slow to load...
    timeout: Option[Duration] = Some(10.seconds),
    s3Bucket: Option[String] = None,
    s3Key: Option[String] = None,
    memorySize: Option[Int] = None,
    policies: Seq[JsObject] = Seq(),
    schedule: Option[String] = None,
    scheduleEnabled: Boolean = true,
    scheduleDescription: Option[String] = None
  )(implicit context: StackContext) =
    for {
      s3Bucket <- IO(s3Bucket.getOrElse(context.s3Bucket))
      s3Key <- IO(s3Key.orElse(context.lambdaFileS3Key).getOrElse(???))
      c = implicitly[ClassTag[T]].runtimeClass
      // TODO: check if class interface meets AWS Lambda requirements
      name =
        if (classOf[LambdaRequestHandler[IO, _, _]].isAssignableFrom(c))
          c.asInstanceOf[Class[LambdaRequestHandler[IO, _, _]]].newInstance.serviceConfiguration.functionName
        else
          c.getSimpleName.replaceAll("\\$", "")
      _ <- IO(logger.debug(s"lambda for $c"))
      roleName = s"lambdaRole${name.capitalize}"
    } yield
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
          FunctionName = Some(name),
          Code = Code(
            S3Bucket = fromString(s3Bucket),
            S3Key = fromString(s3Key)
          ),
          Handler = c.getName,
          Role = `Fn::GetAtt`(Seq(roleName, "Arn")),
          Runtime = Java11,
          Timeout = timeout.map(_.toSeconds.toInt),
          MemorySize = memorySize.map(i => i: Token[Int])
        )

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

  val allowReadWriteSnapshots =
    policy(
      name = "AllowReadWriteSnapshots",
      actions = Seq(
        "ec2:CreateSnapshot",
        "ec2:DeleteSnapshot",
        "ec2:DescribeSnapshots",
        "ec2:ListSnapshot"
      )
    )

  def allowQueryDynamoDbTable(table: String) =
    for {
      accountId <- accountId
    } yield
      policy(
        name = s"AllowQueryDynamoDbTable${table.capitalize}",
        actions = Seq(
          "dynamodb:Query"
        ),
        resource = Seq(
          s"arn:aws:dynamodb:${awsContext.region}:$accountId:table/$table"
        )
      )

  //def s3Bucket(name: Option[String] = None) = `AWS::S3::Bucket`(s"s3Bucket${name.getOrElse(randomUUID.toString)}", name)
}
  