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
ThisBuild / scalaVersion := "2.13.8"
ThisBuild / crossScalaVersions := Seq("2.11.12", "2.12.15", "2.13.8")
ThisBuild / version := "1.3.7"
ThisBuild / scalacOptions += "-target:jvm-1.8"
ThisBuild / javacOptions ++= Seq("-source", "11", "-target", "11")
ThisBuild / Test / packageBin / publishArtifact := true
ThisBuild / Test / packageSrc / publishArtifact := true
ThisBuild / Test / packageDoc / publishArtifact := true
ThisBuild / resolvers += "Mulesoft" at "https://repository.mulesoft.org/nexus/content/repositories/public/"

import sbt.Keys.{description, libraryDependencies, publishConfiguration}
import sbt.addCompilerPlugin
import xerial.sbt.Sonatype._

lazy val commonSettings = Seq(
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),
  publishM2Configuration := publishM2Configuration.value.withOverwrite(true),
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
lazy val AwsVersion = "1.12.471"
lazy val AmazonSqsJavaExtendedClientLibVersion = "1.2.2"
lazy val AwsLambdaJavaCoreVersion = "1.2.1"
lazy val CommonsIoVersion = "2.8.0"
lazy val CommonsLangVersion = "3.12.0"
lazy val CloudformationTemplateGeneratorVersion = "3.10.4"
lazy val SclapVersion = "1.3.6"
lazy val SprintVersion = "0.0.7"
lazy val CirceVersion = "0.12.0-M3"
lazy val SprayJsonVersion = "1.3.6"
lazy val PulsarVersion = "2.10.3"
lazy val ScalaJava8CompatVersion = "1.0.2"
lazy val LogbackVersion = "1.2.3"
lazy val ShapelessVersion = "2.3.3"
lazy val JodaTimeVersion = "2.11.1"
lazy val CondenseVersion = "0.5.4"
lazy val ProguardVersion = "7.2.2"
lazy val ActivemqVersion = "5.16.3"
lazy val JmsVersion = "2.0.1"
lazy val ScalaUriVersion = "1.4.10"

lazy val root: Project = project
  .in(file("."))
  .settings(commonSettings)
  .aggregate(`scase-core`, `scase-aws`, `scase-circe`, `scase-spray-json`, // TODO: add back scase-aws-test
    `scase-pulsar`, `scase-jms`, `scase-activemq`, `scase-tibco-rv`, `scase-tools`)
  .dependsOn(`scase-core`, `scase-aws`, `scase-circe`, `scase-spray-json`,
    `scase-pulsar`, `scase-jms`, `scase-activemq`, `scase-tibco-rv`)

lazy val `scase-core` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % CatsVersion,
      "org.typelevel" %% "cats-effect" % CatsVersion,
      "org.typelevel" %% "cats-testkit-scalatest" % CatsTestkitScalatestVersion % Test,
      "org.typelevel" %% "kittens" % CatsVersion % Test,
      "com.typesafe.scala-logging" %% "scala-logging" % ScalaLoggingVersion,
      "com.lihaoyi" %% "sourcecode" % SourcecodeVersion,
      "org.scalatest" %% "scalatest" % ScalatestVersion % Test,
      "commons-io" % "commons-io" % CommonsIoVersion,
      "org.apache.commons" % "commons-lang3" % CommonsLangVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion % Test,
      "com.chuusai" %% "shapeless" % ShapelessVersion,
      "com.github.sbt" % "junit-interface" % "0.13.2" % Test,
      "io.jobial" %% "sprint" % SprintVersion,
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
      "com.amazonaws" % "aws-java-sdk-ec2" % AwsVersion excludeAll ("commons-logging"),
      "com.amazonaws" % "aws-java-sdk-secretsmanager" % AwsVersion excludeAll ("commons-logging"),
      "org.typelevel" %% "cats-core" % CatsVersion,
      "org.typelevel" %% "cats-effect" % CatsVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % ScalaLoggingVersion
    )
  )
  .dependsOn(`scase-core` % "compile->compile;test->test")
  .dependsOn(`scase-circe` % "test->test")

//lazy val `scase-aws-test` = project
//  .settings(commonSettings)
//  .settings(
//    libraryDependencies ++= Seq(
//      "io.jobial" %% "condense" % CondenseVersion
//    ),
//    cloudformationStackClass := "io.jobial.scase.aws.lambda.TestServiceStack$",
//    Proguard / proguardOptions := Seq(
//      "-injars " + (Test / packageBin).value,
//      "-injars " + (`scase-core` / Test / packageBin).value,
//      "-dontobfuscate", "-dontoptimize", "-dontnote", "-ignorewarnings",
//      "-keep class io.jobial.scase.aws.lambda.** {*;}",
//      "-keep class com.amazonaws.services.lambda.** {*;}",
//      "-keep class scala.Symbol {*;}"
//    ) ++ (Proguard / proguardOptions).value,
//    Proguard / proguardInputFilter := { file =>
//      file.name match {
//        case _ => Some("!META-INF/**,!about.html,!org/apache/commons/logging/**")
//      }
//    },
//    Proguard / proguard / javaOptions := Seq("-Xmx2G"),
//    Proguard / proguardVersion := ProguardVersion
//  )
//  .dependsOn(`scase-core` % "compile->compile;test->test")
//  .dependsOn(`scase-aws` % "compile->compile")
//  .dependsOn(`scase-circe`)

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
      "org.scala-lang.modules" %% "scala-java8-compat" % ScalaJava8CompatVersion,
      "junit" % "junit" % "4.13.2" % Test
    )
  )
  .dependsOn(`scase-core` % "compile->compile;test->test")
  .dependsOn(`scase-circe` % "test->test")
  .dependsOn(`scase-tibco-rv` % "test->test")

lazy val `scase-jms` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "javax.jms" % "javax.jms-api" % JmsVersion,
      "org.apache.activemq" % "activemq-client" % ActivemqVersion % Test
    )
  )
  .dependsOn(`scase-core` % "compile->compile;test->test")
  .dependsOn(`scase-circe` % "test->test")

lazy val `scase-activemq` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.activemq" % "activemq-client" % ActivemqVersion
    )
  )
  .dependsOn(`scase-jms` % "compile->compile;test->test")

lazy val `scase-tibco-rv` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "joda-time" % "joda-time" % JodaTimeVersion
    ),
    Compile / unmanagedJars ++= Seq(file(sys.env.get("TIBCO_RV_ROOT").getOrElse(sys.props("tibco.rv.root")) + "/lib/tibrvj.jar"))
  )
  .dependsOn(`scase-core` % "compile->compile;test->test")
  .dependsOn(`scase-circe`)
  .dependsOn(`scase-spray-json`)

lazy val `scase-tools` = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.jobial" %% "sclap" % SclapVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "io.lemonlabs" %% "scala-uri" % ScalaUriVersion,
      "org.apache.pulsar" % "pulsar-client-admin" % PulsarVersion
    ),
    Compile / packageBin / mappings ~= {
      _.filter(!_._1.getName.endsWith("logback.xml"))
    }
  )
  .dependsOn(`scase-core` % "compile->compile;test->test")
  .dependsOn(`scase-pulsar`)
  .dependsOn(`scase-tibco-rv`)
  .dependsOn(`scase-activemq`)
