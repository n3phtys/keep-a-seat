package org.nephtys.keepaseat.internal

import akka.http.scaladsl.server._
import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import org.nephtys.keepaseat.internal.configs.{Authenticators, PasswordConfig, ServerConfig}

/**
  * Created by nephtys on 9/28/16.
  */
class StaticRoute(rootpathdir : String)(implicit serverConfigSource : ServerConfig, passwordConfig: PasswordConfig) {
  def extractRoute : Route = {
    Authenticators.BasicAuthOrPass(passwordConfig, onlySuperusers = false) { () =>
      getFromDirectory(serverConfigSource.pathToStaticWebDirectory(rootpathdir))
    } ~ Authenticators.BasicAuthOrPass(passwordConfig, onlySuperusers = false) { () => pathSingleSlash {
      get(redirect("./index.html", PermanentRedirect))
    }
    }
  }
}
