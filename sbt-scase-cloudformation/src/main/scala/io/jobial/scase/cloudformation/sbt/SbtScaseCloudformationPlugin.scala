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
package io.jobial.scase.cloudformation.sbt


import sbt.{io, _}
import Keys._
import sbt.State.stateOps
import complete.DefaultParsers._
import sbtassembly.AssemblyKeys.{assembly, assemblyOutputPath}
import sbtassembly.AssemblyPlugin.autoImport.assemblyJarName

import scala.collection.JavaConverters._
import scala.io.Source

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
object SbtScaseCloudformationPlugin extends AutoPlugin {
  override def trigger = allRequirements
  //override lazy val buildSettings = Seq(commands += helloCommand)

  object autoImport {
    val scaseCloudformation = inputKey[Unit]("Scase Cloudformation Plugin")
    val cloudformationStackClass = settingKey[String]("cloudformationStackClass")
//    val helloTask = taskKey[Unit]("say hello")
  }
  import autoImport._

  override lazy val buildSettings = Seq(
    cloudformationStackClass := ""
  )

  override def requires = super.requires && sbtassembly.AssemblyPlugin

  
  
  override lazy val projectSettings = Seq(
    scaseCloudformation := {
      val args = spaceDelimited("").parsed
      
      //println("cloudformationStackClass: " + cloudformationStackClass.value)
      if (cloudformationStackClass.value != "") {
        println(s"scaseCloudformation called with args ${args.toList} for " + cloudformationStackClass.value)
        //println(Class.forName(cloudformationStackClass.value))
        println((assemblyJarName in assembly).value)
        println((assemblyOutputPath in assembly).value)
        println((fullClasspath in assembly).value)
        println(sys.props("java.class.path"))
        val processBuilder = new ProcessBuilder
        val c = processBuilder.inheritIO.command((List("java", "-cp", (fullClasspath in assembly).value.map(_.data.toString).mkString(":"), cloudformationStackClass.value, "create-stack")).asJava)
        val process = c.start()
        import java.io.BufferedReader
        //val reader = new BufferedReader(new Nothing(process.getInputStream))

        //Source.fromInputStream(process.getInputStream).getLines.foreach(println)
        val exitVal = process.waitFor
        println(exitVal)
      }
    }
  )

}