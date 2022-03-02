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
ThisBuild / scalaVersion := "2.12.13"
ThisBuild / crossScalaVersions := Seq("2.11.12", "2.12.13", "2.13.6")
ThisBuild / version := "0.4.0"
ThisBuild / scalacOptions += "-target:jvm-1.8"
ThisBuild / publishArtifact in(Test, packageBin) := true
ThisBuild / publishArtifact in(Test, packageSrc) := true
ThisBuild / publishArtifact in(Test, packageDoc) := true

import sbt.Defaults.sbtPluginExtra
import sbt.Keys.{description, libraryDependencies, publishConfiguration}
import sbt.addCompilerPlugin
import xerial.sbt.Sonatype._

lazy val commonSettings = Seq(
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),
  publishTo := publishTo.value.orElse(sonatypePublishToBundle.value),
  sonatypeProjectHosting := Some(GitHubHosting("jobial-io", "scase", "orbang@jobial.io")),
  organizationName := "Jobial OÜ",
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  description := "Run functional Scala code as a portable serverless function or microservice",
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  scalacOptions ++= (if (scalaBinaryVersion.value != "2.13") Seq("-Ypartial-unification") else Seq())
)

lazy val CatsVersion = "2.0.0"
lazy val CatsTestkitScalatestVersion = "1.0.0-RC1"
lazy val ScalaLoggingVersion = "3.9.2"
lazy val ScalatestVersion = "3.2.3"
lazy val SourcecodeVersion = "0.2.3"
lazy val AwsVersion = "1.11.557"
lazy val AmazonSqsJavaExtendedClientLibVersion = "1.2.2"
lazy val AwsLambdaJavaCoreVersion = "1.2.1"
lazy val CommonsIoVersion = "2.8.0"
lazy val CommonsLangVersion = "3.12.0"
lazy val CloudformationTemplateGeneratorVersion = "3.10.4"
lazy val SclapVersion = "1.1.7"
lazy val CirceVersion = "0.12.0-M3"
lazy val SprayJsonVersion = "1.3.6"
lazy val PulsarVersion = "2.9.0"
lazy val ZioVersion = "2.0.0.0-RC13" // TODO: upgrade when Cats version is upgraded
lazy val ScalaJava8CompatVersion = "1.0.2"
lazy val LogbackVersion = "1.2.3"
lazy val ShapelessVersion = "2.3.3"

lazy val root: Project = project
  .in(file("."))
  .settings(commonSettings)
  .settings(
    publishArtifact := false,
    makePom / publishArtifact := true
  )
  .aggregate(`scase-core`, `scase-aws`, `scase-circe`, `scase-spray-json`, `scase-spray-json-example`,
    `scase-sqs-example`, `scase-pulsar`, `scase-jms`, `scase-pulsar-example`, `scase-zio-example`)
  .dependsOn(`scase-core`, `scase-aws`, `scase-circe`, `scase-spray-json`, `scase-spray-json-example`,
    `scase-sqs-example`, `scase-pulsar`, `scase-jms`, `scase-pulsar-example`, `scase-zio-example`)

lazy val `scase-core` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % CatsVersion,
      "org.typelevel" %% "cats-effect" % CatsVersion,
      "org.typelevel" %% "cats-testkit-scalatest" % CatsTestkitScalatestVersion % Test,
      "com.typesafe.scala-logging" %% "scala-logging" % ScalaLoggingVersion,
      "com.lihaoyi" %% "sourcecode" % SourcecodeVersion,
      "org.scalatest" %% "scalatest" % ScalatestVersion % Test,
      "commons-io" % "commons-io" % CommonsIoVersion,
      "org.apache.commons" % "commons-lang3" % CommonsLangVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion % Test,
      "com.chuusai" %% "shapeless" % ShapelessVersion
    )
  )

lazy val `scase-aws` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-sqs" % AwsVersion excludeAll ("commons-logging"),
      "com.amazonaws" % "amazon-sqs-java-extended-client-lib" % AmazonSqsJavaExtendedClientLibVersion excludeAll ("commons-logging"),
      "org.slf4j" % "jcl-over-slf4j" % "1.7.32",
      "commons-logging" % "commons-logging-api" % "1.1",
      "com.amazonaws" % "aws-java-sdk-lambda" % AwsVersion excludeAll ("commons-logging"),
      "com.amazonaws" % "aws-java-sdk-cloudformation" % AwsVersion excludeAll ("commons-logging"),
      "com.amazonaws" % "aws-lambda-java-core" % AwsLambdaJavaCoreVersion excludeAll ("commons-logging"),
      "com.amazonaws" % "aws-java-sdk-sts" % AwsVersion excludeAll ("commons-logging"),
      "org.typelevel" %% "cats-core" % CatsVersion,
      "org.typelevel" %% "cats-effect" % CatsVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % ScalaLoggingVersion
    )
  )
  .dependsOn(`scase-core` % "compile->compile;test->test")
  .dependsOn(`scase-circe` % "test->test")

lazy val `scase-spray-json` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-json" % SprayJsonVersion
    ),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % CirceVersion)
  )
  .dependsOn(`scase-core` % "compile->compile;test->test")

lazy val `scase-circe` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % CirceVersion))
  .dependsOn(`scase-core` % "compile->compile;test->test")

lazy val `scase-pulsar` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.pulsar" % "pulsar-client" % PulsarVersion,
      "org.scala-lang.modules" %% "scala-java8-compat" % ScalaJava8CompatVersion
    )
  )
  .dependsOn(`scase-core` % "compile->compile;test->test")
  .dependsOn(`scase-circe` % "test->test")

lazy val `scase-jms` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "javax.jms" % "javax.jms-api" % "2.0.1",
      "org.apache.activemq" % "activemq-client" % "5.16.3" % Test
    )
  )
  .dependsOn(`scase-core` % "compile->compile;test->test")
  .dependsOn(`scase-circe` % "test->test")

lazy val `scase-pulsar-example` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.jobial" %% "sclap" % SclapVersion
    )
  )
  .dependsOn(`scase-circe` % "compile->compile;test->test")
  .dependsOn(`scase-pulsar`)

lazy val `scase-sqs-example` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.jobial" %% "sclap" % SclapVersion
    )
  )
  .dependsOn(`scase-aws` % "compile->compile;test->test")
  .dependsOn(`scase-circe` % "compile->compile;test->test")

lazy val `scase-spray-json-example` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.jobial" %% "sclap" % SclapVersion
    )
  )
  .dependsOn(`scase-spray-json` % "compile->compile;test->test")
  .dependsOn(`scase-pulsar`)

lazy val `scase-zio-example` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-interop-cats" % ZioVersion,
      "io.jobial" %% "sclap-zio" % SclapVersion
    )
  )
  .dependsOn(`scase-circe` % "compile->compile;test->test")
  .dependsOn(`scase-core` % "compile->compile;test->test")
