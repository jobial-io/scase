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
ThisBuild / version := "0.1.0"
ThisBuild / isSnapshot := true
ThisBuild / scalacOptions += "-target:jvm-1.8"

ThisBuild / assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

import sbt.Keys.{description, libraryDependencies, publishConfiguration}
import sbtassembly.AssemblyPlugin.autoImport.{ShadeRule, assemblyPackageScala}
import xerial.sbt.Sonatype._

lazy val commonSettings = Seq(
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),
  publishTo := publishTo.value.orElse(sonatypePublishToBundle.value),
  sonatypeProjectHosting := Some(GitHubHosting("jobial-io", "scase", "orbang@jobial.io")),
  organizationName := "Jobial OÜ",
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)
)

lazy val CatsVersion = "2.0.0"
lazy val ScalaLoggingVersion = "3.9.2"
lazy val ScalatestVersion = "3.2.3"
lazy val SourcecodeVersion = "0.2.3"
lazy val AwsVersion = "1.11.557"
lazy val AmazonSqsJavaExtendedClientLibVersion = "1.2.2"
lazy val AwsLambdaJavaCoreVersion = "1.2.1"
lazy val CommonsIoVersion = "2.8.0"
lazy val CommonsLangVersion = "3.12.0"
lazy val CloudformationTemplateGeneratorVersion = "3.10.4"
lazy val SclapVersion = "1.1.5"
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
    makePom / publishArtifact := true,
    assemblyPackageScala / assembleArtifact := false,
    assemblyPackageDependency / assembleArtifact := false
  )
  .aggregate(`scase-core`, `scase-aws`, `scase-cloudformation`, `scase-spray-json`, `scase-lambda-example`, `scase-pulsar-example`, `sbt-scase-cloudformation`)
  .dependsOn(`scase-core`, `scase-aws`, `scase-cloudformation`, `scase-spray-json`, `scase-lambda-example`, `scase-pulsar-example`, `sbt-scase-cloudformation`)

lazy val `scase-core` = project
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
      "ch.qos.logback" % "logback-classic" % LogbackVersion % "test",
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
      "com.amazonaws" % "aws-java-sdk-sts" % AwsVersion excludeAll ("commons-logging")
    ),
    assemblyPackageScala / assembleArtifact := false,
    assemblyPackageDependency / assembleArtifact := false
  )
  .dependsOn(`scase-core` % "compile->compile;test->test")
  .dependsOn(`scase-circe` % "test->test")

lazy val `scase-cloudformation` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.jobial" %% "cloud-formation-template-generator" % CloudformationTemplateGeneratorVersion,
      "io.jobial" %% "sclap" % SclapVersion
    ),
    assemblyPackageScala / assembleArtifact := false,
    assemblyPackageDependency / assembleArtifact := false
  )
  .dependsOn(`scase-aws` % "compile->compile;test->test")

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


// check https://stackoverflow.com/questions/37525980/sbt-exclude-module-from-aggregates-or-compilation-based-on-scala-version
lazy val `sbt-scase-cloudformation` = project
  .settings(
    name := "sbt-scase-cloudformation",
    publish := scalaBinaryVersion.value == "2.12",
    //      unmanagedSources / excludeFilter := AllPassFilter,
    //      managedSources / excludeFilter := AllPassFilter,
    Compile / unmanagedSourceDirectories := (if (scalaBinaryVersion.value == "2.12") Seq(baseDirectory.value / "src" / "main" / "scala") else Nil),
    //      Compile / managedSourceDirectories := Nil,
    publishMavenStyle := scalaBinaryVersion.value == "2.12",
    sbtPlugin := scalaBinaryVersion.value == "2.12",
    pluginCrossBuild / sbtVersion := "1.2.8" // set minimum sbt version
  )

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

lazy val `scase-pulsar-example` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.jobial" %% "sclap" % SclapVersion
    )
  )
  .dependsOn(`scase-circe` % "compile->compile;test->test")
  .dependsOn(`scase-pulsar`)

lazy val `scase-lambda-example` = project
  .settings(commonSettings)
  //.enablePlugins(SbtScaseCloudformationPlugin)
  .settings(
    //assembly / assemblyJarName := "utils.jar",
    assemblyShadeRules := Seq(
      ShadeRule.keep("io.jobial.scase.aws.lambda.example.HelloExample").inAll,
    )
    // cloudformationStackClass := "io.jobial.scase.example.greeting.GreetingServiceStack"
  )
  .dependsOn(`scase-aws` % "compile->compile;test->test")
  .dependsOn(`scase-circe` % "compile->compile;test->test")
  .dependsOn(`scase-cloudformation` % "compile->compile;test->test")

lazy val `scase-sqs-example` = project
  .settings(commonSettings)
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
