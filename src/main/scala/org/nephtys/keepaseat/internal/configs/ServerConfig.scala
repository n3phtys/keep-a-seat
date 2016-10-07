package org.nephtys.keepaseat.internal.configs

/**
  * Created by nephtys on 9/28/16.
  */
trait ServerConfig {

  def httpsPassword : Option[String] //this should be easily convertable to Array[Char], so no special signs

  def port : Int

  //assume "web" as default value
  def pathToStaticWebDirectory : String


  def filepathToDatabaseWithoutFileEnding : Option[String]
}
