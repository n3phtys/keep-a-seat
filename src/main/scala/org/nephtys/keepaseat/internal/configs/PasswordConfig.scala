package org.nephtys.keepaseat.internal.configs

/**
  * Created by nephtys on 9/28/16.
  */
trait PasswordConfig {


  /**
    * also takes superuser credentials!
    * @param username
    * @param password
    * @return
    */
  def correctCredentialsForUser(username : String, password : String) : Boolean

  def superuserCredentials(username : String, password : String) : Boolean


  def realmForCredentials() : String
}
