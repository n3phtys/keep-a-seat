package org.nephtys.keepaseat.internal.configs

/**
  * Created by nephtys on 9/28/16.
  */
trait ServerConfig {

  //TODO: Changes here can only be done via true restart. Maybe an infinite loop with retry works too as a main? for
  // now, use the new CriticalConfigChangedException if the newly loaded source file differs in a critical section


  def https : Boolean

  def httpsOnly : Option[Boolean]

  //ip, hostname, url, even localhost
  def url : String
  def port : Int

  //can be empty
  def rootpath : Option[String]
}
