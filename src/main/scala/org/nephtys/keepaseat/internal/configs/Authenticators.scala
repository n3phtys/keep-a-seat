package org.nephtys.keepaseat.internal.configs

import akka.http.scaladsl.server.directives.Credentials

/**
  * Created by nephtys on 9/30/16.
  */
object Authenticators {

  def normalUserOrSuperuserAuthenticator(config : PasswordConfig)(credentials : Credentials) : Option[String] = {
    credentials match {
      case p @ Credentials.Provided(id) if (id == config.normalUser.username && p.verify(config.normalUser.password)
        ) || (id == config.superUser.username && p.verify(config.superUser.password)
        ) => Some(id)
      case _ => None
    }
  }

  def onlySuperuserAuthenticator(config : PasswordConfig)(credentials : Credentials) : Option[String] = {
    credentials match {
      case p @ Credentials.Provided(id) if id == config.superUser.username && p.verify(config.superUser.password) => Some(id)
      case _ => None
    }
  }
}
