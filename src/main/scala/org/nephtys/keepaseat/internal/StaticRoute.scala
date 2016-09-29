package org.nephtys.keepaseat.internal

import akka.http.scaladsl.server._
import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import org.nephtys.keepaseat.internal.configs.ServerConfig

/**
  * Created by nephtys on 9/28/16.
  */
class StaticRoute()(implicit serverConfigSource : () => ServerConfig) {

  def extractRoute : Route = {
    println(serverConfigSource.apply().pathToStaticWebDirectory)
    getFromDirectory(serverConfigSource.apply().pathToStaticWebDirectory) ~ pathSingleSlash(
      get(redirect("index.html", PermanentRedirect))
    )
  }
}
