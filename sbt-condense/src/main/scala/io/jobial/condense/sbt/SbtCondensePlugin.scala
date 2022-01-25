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
package io.jobial.condense.sbt

import com.lightbend.sbt.SbtProguard
import com.lightbend.sbt.SbtProguard.autoImport.Proguard
import sbt.Keys._
import sbt.{Runtime, _}
import complete.DefaultParsers._

import scala.collection.JavaConverters._

/**
 * Loosely based on
 *
 * https://codewithstyle.info/how-to-build-a-simple-sbt-plugin/
 * https://stackoverflow.com/questions/8973666/how-to-access-a-sbt-projects-settings-in-a-plugin
 * https://stackoverflow.com/questions/22372717/sbt-how-to-use-classes-from-build-sbt-inside-plugin-task-execution
 * https://stackoverflow.com/questions/37369884/sbt-how-to-refer-to-other-project-source-code-in-build-sbt
 * https://stackoverflow.com/questions/23409993/defining-sbt-task-that-invokes-method-from-project-code
 *
 * sbt compile publishLocal
 * sbt "scaseCloudformation 1 2 3"
 */
object SbtCondensePlugin extends AutoPlugin {
  override def trigger = allRequirements
  //override lazy val buildSettings = Seq(commands += helloCommand)

  object autoImport {
    val condense = inputKey[Unit]("Condense Plugin")
    val cloudformationStackClass = settingKey[String]("cloudformationStackClass")
    //    val helloTask = taskKey[Unit]("say hello")
  }

  import autoImport._

  override lazy val buildSettings = Seq(
    cloudformationStackClass := ""
  )

  override def requires = super.requires && SbtProguard

  override lazy val projectSettings = Seq(
    condense := {
      val args = spaceDelimited("").parsed

      //println("cloudformationStackClass: " + cloudformationStackClass.value)
      if (cloudformationStackClass.value != "") {
        println(s"scaseCloudformation called with args ${args.toList} for " + cloudformationStackClass.value)
        //println(Class.forName(cloudformationStackClass.value))
        println((artifactPath in Proguard).value)
        println(sys.props("java.class.path"))
        val processBuilder = new ProcessBuilder
        val commandLine = List("java", "-cp", (Runtime / fullClasspath).value.map(_.data.toString).mkString(":"),
          "io.jobial.condense.Condense", s"--lambda-file=${(artifactPath in Proguard).value}", cloudformationStackClass.value, "create-or-update")
        println(commandLine)
        val c = processBuilder.inheritIO.command(commandLine.asJava)
        val process = c.start()
        //val reader = new BufferedReader(new Nothing(process.getInputStream))

        //Source.fromInputStream(process.getInputStream).getLines.foreach(println)
        val exitVal = process.waitFor
        println(exitVal)
      }
    }
  )

}