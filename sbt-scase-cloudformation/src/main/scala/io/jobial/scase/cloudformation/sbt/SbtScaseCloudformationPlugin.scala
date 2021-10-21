package io.jobial.scase.cloudformation.sbt


import sbt._
import Keys._
import sbt.State.stateOps
import complete.DefaultParsers._

// Loosely based on https://codewithstyle.info/how-to-build-a-simple-sbt-plugin/
// https://stackoverflow.com/questions/8973666/how-to-access-a-sbt-projects-settings-in-a-plugin

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
      println(s"scaseCloudformation called with args ${args.toList} for " + cloudformationStackClass.value)
    }
  )

}