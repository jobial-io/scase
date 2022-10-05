package io.jobial.scase.core.impl

import org.scalatest.flatspec.AnyFlatSpec

class RegexUtilsTest extends AnyFlatSpec with RegexUtils {

  "isProbablyRegex" should "work" in {
    assert(!isProbablyRegex("hello world"))
    assert(isProbablyRegex("a.*"))
    assert(isProbablyRegex("^a"))
    assert(isProbablyRegex("a$"))
    assert(isProbablyRegex("a+"))
    assert(isProbablyRegex("[ab]"))
    assert(isProbablyRegex("(ab)"))
    assert(isProbablyRegex("a.b"))
  }
}
