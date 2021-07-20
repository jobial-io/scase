package io.jobial.scase.aws.client

import java.math.BigInteger
import java.security.MessageDigest
import java.util.{Arrays, UUID}

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object Hash {

  /** Variable length hash */
  def hash(input: String, len: Int = 16, radix: Int = 36) = {
    var key = input.getBytes("UTF-8")
    val sha = MessageDigest.getInstance("SHA-1")
    key = sha.digest(key)
    key = Arrays.copyOf(key, 16) // use only the first 128 bit

    // Generate the secret key specs.
    val secretKeySpec = new SecretKeySpec(key, "AES")

    // Instantiate the cipher
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)

    val encrypted = cipher.doFinal(key)
    new BigInteger(1, encrypted).toString(radix).substring(0, len)
  }

  def uuid(len: Int = 16, radix: Int = 36) =
    hash(UUID.randomUUID.toString, len, radix)
}