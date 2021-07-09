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

name := "scase"

ThisBuild / organization := "io.jobial"
ThisBuild / scalaVersion := "2.13.6"
ThisBuild / crossScalaVersions := Seq("2.11.12", "2.12.13", "2.13.6")
ThisBuild / version := "0.1.0"

import sbt.Keys.{description, publishConfiguration}
import xerial.sbt.Sonatype._

lazy val commonSettings = Seq(
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),
  publishTo := publishTo.value.orElse(sonatypePublishToBundle.value),
  sonatypeProjectHosting := Some(GitHubHosting("jobial-io", "scase", "orbang@jobial.io")),
  organizationName := "Jobial OÜ",
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
)

lazy val CatsVersion = "2.0.0"
lazy val ScalaLoggingVersion = "3.9.2"
lazy val ScalatestVersion = "3.2.3"
lazy val SourcecodeVersion = "0.2.3"
lazy val AwsVersion = "1.11.557"
lazy val AwsLambdaJavaCoreVersion = "1.2.1"
lazy val CommonsIoVersion = "2.8.0"
lazy val CommonsLangVersion = "3.12.0"
lazy val CloudformationTemplateGeneratorVersion = "3.10.5-SNAPSHOT"
lazy val SclapVersion = "1.0.0"

lazy val root: Project = project
  .in(file("."))
  .settings(commonSettings)
  .settings(
    publishArtifact := false,
    publishArtifact in makePom := true
  )
  .aggregate(`scase-core`, `scase-aws`, `scase-cloudformation`)
  .dependsOn(`scase-core`, `scase-aws`, `scase-cloudformation`)

lazy val `scase-core` = project
  .in(file("scase-core"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % CatsVersion,
      "org.typelevel" %% "cats-effect" % CatsVersion,
      "org.typelevel" %% "cats-free" % CatsVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % ScalaLoggingVersion,
      "com.lihaoyi" %% "sourcecode" % SourcecodeVersion,
      "org.scalatest" %% "scalatest" % ScalatestVersion % "test",
      "commons-io" % "commons-io" % CommonsIoVersion,
      "org.apache.commons" % "commons-lang3" % CommonsLangVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3" % "test"
    )
  )

lazy val `scase-aws` = project
  .in(file("scase-aws"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-sqs" % AwsVersion,
      "com.amazonaws" % "amazon-sqs-java-extended-client-lib" % "master-SNAPSHOT",
      "com.amazonaws" % "aws-java-sdk-lambda" % AwsVersion,
      "com.amazonaws" % "aws-java-sdk-cloudformation" % AwsVersion,
      "com.amazonaws" % "aws-lambda-java-core" % AwsLambdaJavaCoreVersion
    )
  )
  .dependsOn(`scase-core` % "compile->compile;test->test")

lazy val `scase-cloudformation` = project
  .in(file("scase-cloudformation"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.bayer" %% "cloud-formation-template-generator" % CloudformationTemplateGeneratorVersion,
      "io.jobial" %% "sclap" % SclapVersion
    )
  )
  .dependsOn(`scase-aws` % "compile->compile;test->test")
