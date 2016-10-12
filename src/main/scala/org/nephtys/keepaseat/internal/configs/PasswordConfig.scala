package org.nephtys.keepaseat.internal.configs

import org.nephtys.cmac.BasicAuthHelper.LoginData

/**
  * Created by nephtys on 9/28/16.
  */
trait PasswordConfig {

  //maybe require passwords as hashed values in config files for security?

  def normalUser : LoginData
  def superUser : LoginData


  def realmForCredentials : String
}
