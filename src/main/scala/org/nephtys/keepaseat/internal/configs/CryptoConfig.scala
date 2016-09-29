package org.nephtys.keepaseat.internal.configs

import javax.crypto.SecretKey

/**
  * Created by nephtys on 9/28/16.
  */
trait CryptoConfig {

  def relativePathToSecretKeyFile : String

  def bitsForSecretKeyGen : Int

  def keyAlgorithmName : String
}