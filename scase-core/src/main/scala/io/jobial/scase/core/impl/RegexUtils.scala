package io.jobial.scase.core.impl

import scala.util.Try


trait RegexUtils {

  /**
   * Simple heuristics to decide if string could be a regex pattern
   */
  def isProbablyRegex(pattern: String) =
    Try(pattern.r).map(_ => pattern.matches(".*([*+^$.(){}\\r\\n\\[/\\\\]|\\\\.|\\[(?:[^\\r\\n\\]\\\\]|\\\\.)*\\])+.*")).getOrElse(false)
}