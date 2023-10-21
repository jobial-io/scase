package io.jobial.scase.aws.client

import cats.implicits.catsKernelStdOrderForString
import cats.implicits.catsSyntaxEq

trait Tagged[A] {

  def tags(tagged: A): List[Tag]

  def tagValue(tagged: A, key: String) =
    tags(tagged).find(_.key === key).map(_.value)
}

object Tagged {

  def apply[A: Tagged] = implicitly[Tagged[A]]

  implicit class TaggedSyntax[A: Tagged](tagged: A) {
    def tags = Tagged[A].tags(tagged)

    def tagValue(key: String) = Tagged[A].tagValue(tagged, key)
  }
}

case class Tag(key: String, value: String)