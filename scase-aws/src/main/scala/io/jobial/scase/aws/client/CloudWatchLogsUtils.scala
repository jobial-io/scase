package io.jobial.scase.aws.client

import scala.Console._
import scala.util.matching.Regex

object CloudWatchLogsUtils {

  implicit class ColorStringEx(s: String) {
    def replaceFirst(r: Regex, t: String) =
      r.replaceFirstIn(s, t)

    def color(r: Regex, color: String) =
      replaceFirst(r, f"$$1${RESET}${color}$$2${RESET}$$3")
  }

  val datePattern = "^()([12][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] (?:[0-9][0-9]:[0-9][0-9]:[0-9][0-9]\\.[0-9][0-9][0-9])?)(.+)".r
  val threadPattern = "(.+? )(\\[[^]]+\\])( .+ - )".r
  val logLevelPattern = "(.+? )(TRACE|DEBUG|INFO|WARN|ERROR)( .+ - )".r
  val classPattern = "(.+? )((?:[a-z]+\\.)+[a-zA-Z\\$]+)(.+ - )".r
}  

