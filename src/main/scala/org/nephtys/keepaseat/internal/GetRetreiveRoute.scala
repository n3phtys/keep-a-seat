package org.nephtys.keepaseat.internal

import akka.http.scaladsl.server.Route
import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller

/**
  * Created by nephtys on 9/28/16.
  */
class GetRetreiveRoute {

  //TODO: Basic Auth Check

  def extractRoute : Route = ???

/*
  def getR: Route = path(s"get") {
    extractRequest { request =>
      parameter('id.as[Long].?) { id =>

        val user: Option[JWT] = request.headers.find(_.is("Authorization")).flatMap(r => AuthCenter
          .FromHeaderValue(r.value()))

        if (user.isEmpty) {
          reject
        } else {
          if (id.isDefined) {
            //get single detail
            val found = id.flatMap(i => get(new ID(i))(user.get))
            if (found.isEmpty) {
              //reject (id does not exist)
              reject
            } else {
              //send as json
              complete {
                found.get.toJson
              }
            }
          } else {
            //get general short and send them as json
            complete {
              s"[${get()(user.get).map(_.toJson).mkString(",")}]"
            }
          }
        }
      }
    }
  }*/
}
