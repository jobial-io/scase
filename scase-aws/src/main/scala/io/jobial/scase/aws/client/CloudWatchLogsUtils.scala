package io.jobial.scase.aws.client

import scala.Console.RESET
import scala.Console._
import scala.util.matching.Regex

trait CloudWatchLogsUtils {

  implicit class ColorStringEx(s: String) {
    def replaceFirst(r: Regex, t: String) =
      r.replaceFirstIn(s, t)

    def color(r: Regex, color: String) =
      replaceFirst(r, f"$$1${RESET}${color}$$2${RESET}$$3")
  }

  val datePattern = "^()([12][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] (?:[0-9][0-9]:[0-9][0-9]:[0-9][0-9]\\.[0-9][0-9][0-9])?)(.+)".r
  val threadPattern = "(.+? )(\\[[^]]+\\])( .+ - )".r
  val logLevelPattern = "(.+? )(TRACE|DEBUG|INFO|WARN|ERROR)( .+ - )".r
  val errorLogLevelPattern = "(.+? )(ERROR)( .+ - )".r
  val warnLogLevelPattern = "(.+? )(WARN)( .+ - )".r
  val classPattern = "(.+? )((?:[^ :\\-\\.]+\\.)+[^ :\\-\\.]+)(.*- )".r
}  

