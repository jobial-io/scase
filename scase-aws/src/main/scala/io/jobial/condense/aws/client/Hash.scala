/*
 * Copyright (c) 2020 Jobial OÜ. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package io.jobial.condense.aws.client

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