package org.nephtys.keepaseat.internal.configs

import akka.http.scaladsl.server.{Directive, Route}
import akka.http.scaladsl.server.Directives.authenticateBasic
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

  def BasicAuthOrPass(passwordConfig : PasswordConfig, onlySuperusers : Boolean)(routeInner : () => Route) : Route = {
    if (passwordConfig.hasPasswords) {
      authenticateBasic(passwordConfig.realmForCredentials, if(onlySuperusers) onlySuperuserAuthenticator(passwordConfig) else normalUserOrSuperuserAuthenticator
      (passwordConfig)) {
        username => routeInner.apply()
      }
    } else {
      routeInner.apply()
    }

  }
}
