package io.jobial.scase.cloudformation.sbt


import sbt._
import Keys._
import sbt.State.stateOps
import complete.DefaultParsers._

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
      
      println("cloudformationStackClass: " + cloudformationStackClass.value)
      println(s"scaseCloudformation called with args ${args.toList}")
    }
  )

}