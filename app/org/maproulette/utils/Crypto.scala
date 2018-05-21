package org.maproulette.utils

import java.security.SecureRandom
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
  val secretKey = Arrays.copyOf(config.config.get[String]("play.http.secret.key").getBytes("UTF-8"), 16)
  val iv = {
    val secureRandom = new SecureRandom()
    val ivBytes = new Array[Byte](16)
    secureRandom.nextBytes(ivBytes)
    new IvParameterSpec(ivBytes)
  }

  def decrypt(value: String): String = {
    val skeySpec = new SecretKeySpec(secretKey, "AES")
    val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv)
    val original = cipher.doFinal(Base64.decodeBase64(value))
    new String(original)
  }

  def encrypt(value: String): String = {
    val skeySpec = new SecretKeySpec(secretKey, "AES")
    val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv)
    val encrypted = cipher.doFinal(value.getBytes)
    Base64.encodeBase64String(encrypted)
  }
}
