package org.nephtys.keepaseat.internal

import akka.http.scaladsl.server._
import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller

/**
  * Created by nephtys on 9/28/16.
  */
class LinkJWTRoute {

  //this does not require basic auth, but being safe is always better

  //TODO: Basic Auth Check

  val pathToEmailConfirmation : String = ???
  val pathToSuperuserConfirmation : String = ???


  val AuthorizationHeaderName : String = """Authorization"""

  def extractRoute : Route = ???


  def emailConfirmationRoute: Route = path(pathToEmailConfirmation) {
  extractRequest { request =>
    parameter('jwt.as[String]) { jwtAsB64String =>
      try {
        val login : Option[org.nephtys.cmac.BasicAuthHelper.LoginData] = request.headers.find(_.is
        (AuthorizationHeaderName)).flatMap(headervalue => org.nephtys.cmac.BasicAuthHelper
          .extractPasswordFromAuthenticationHeaderValue(headervalue.value()))

        /**
          * if this is Some(false), the user is not authorized, but authenticated, so return 403. Use in case of
          * superuser only
          * resources. Some(true) is perfectly okay, None means return 401
          */
        val checkedLogin : Option[Boolean] = ???

        checkedLogin match {
          case None => ???
          case Some(false) => ???
          case Some(true) => {
            ???
            //TODO check if the jwt is all actually correct, reject with 403 if not

            complete("Your Reservation was successfully registered and your email address confirmed. The Administrator " +
              "will be " +
              "presented with your registration, and confirm or decline it soon. You will be notified by email in either " +
              "way. Thank you for your understanding!")
          }
        }


      } catch {
        case e : Exception => reject
      }

      /*val user: Option[JWT] = request.headers.find(_.is("Authorization")).flatMap(r => AuthCenter
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
      }*/
    }
  }
}
}
