package io.jobial.scase.monitoring

case class SourceContext(sourceFile: sourcecode.File, sourceLine: sourcecode.Line) {
  def description = s"(${sourceFile.value}:${sourceLine.value})"
}

