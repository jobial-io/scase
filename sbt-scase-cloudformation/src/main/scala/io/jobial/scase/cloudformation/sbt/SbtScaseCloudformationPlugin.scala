package io.jobial.scase.cloudformation.sbt


import sbt._
import Keys._
import sbt.State.stateOps
import complete.DefaultParsers._

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
  
  override lazy val projectSettings = Seq(
    scaseCloudformation := {
      val args = spaceDelimited("").parsed
      
      //println("cloudformationStackClass: " + cloudformationStackClass.value)
      if (cloudformationStackClass.value != "") {
        println(s"scaseCloudformation called with args ${args.toList} for " + cloudformationStackClass.value)
        println(Class.forName(cloudformationStackClass.value))
      }
    }
  )

}