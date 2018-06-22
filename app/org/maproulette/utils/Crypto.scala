package org.maproulette.utils

import java.security.{MessageDigest, SecureRandom}
import java.util.Arrays

import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import javax.crypto.Cipher
import javax.inject.{Inject, Singleton}
import org.apache.commons.codec.binary.Base64
import org.maproulette.Config

/**
  * @author mcuthbert
  */
@Singleton
class Crypto @Inject()(config:Config) {
  val key = config.config.get[String]("play.http.secret.key")

  def encrypt(value: String): String = {
    val cipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, keyToSpec)
    Base64.encodeBase64String(cipher.doFinal(value.getBytes("UTF-8")))
  }

  def decrypt(encryptedValue: String): String = {
    val cipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING")
    cipher.init(Cipher.DECRYPT_MODE, keyToSpec)
    new String(cipher.doFinal(Base64.decodeBase64(encryptedValue)))
  }

  def keyToSpec: SecretKeySpec = {
    var keyBytes: Array[Byte] = (SALT + key).getBytes("UTF-8")
    val sha: MessageDigest = MessageDigest.getInstance("SHA-1")
    keyBytes = sha.digest(keyBytes)
    keyBytes = Arrays.copyOf(keyBytes, 16)
    new SecretKeySpec(keyBytes, "AES")
  }

  private val SALT: String = "jMhKlOuJnM34G6NHkqo9V010GhLAqOpF0BePojHgh1HgNg8^72k"
}
