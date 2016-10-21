package org.nephtys.keepaseat.internal

import java.net.URI

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, _}
import org.nephtys.cmac.MacSource
import org.nephtys.keepaseat.filter.XSSCleaner
import org.nephtys.keepaseat.internal.configs.{Authenticators, PasswordConfig}
import org.nephtys.keepaseat.internal.eventdata.Event
import org.nephtys.keepaseat.internal.posts.{SimpleSuperuserPost, SimpleUserPost}
import org.nephtys.keepaseat.internal.validators.{SuperuserPostValidator, UserPostValidator}
import org.nephtys.keepaseat.{Databaseable, MailNotifiable}
import upickle.default._

import scala.util.{Failure, Success, Try}

/**
  * Created by nephtys on 9/28/16.
  */
class PostChangesRoute(implicit passwordConfig: PasswordConfig, mailer: MailNotifiable,
                       database: Databaseable, macSource: MacSource,
                       xssCleaner: XSSCleaner, validatorsUser: Seq[UserPostValidator], validatorsSuperuser:
                       Seq[SuperuserPostValidator]) {

  import PostChangesRoute._


  def extractRoute: Route = userpostroute ~ superuserpostroute

  def userpostroute = path(userPostPathWithoutSlashes) {
    csrfCheckForSameOrigin { headers =>
    authenticateBasic(passwordConfig.realmForCredentials, Authenticators.normalUserOrSuperuserAuthenticator
      (passwordConfig)) { username =>
        post {
          entity(as[String]) { jsonstring => {
            println(s"Incoming post: $jsonstring")
            Try(read[SimpleUserPost](jsonstring).sanitizeHTML.validateWithException) match {
              case Success(securedUserPost) => {
                val host : String = headers.find(_.is(XForwardedHostHeader.toLowerCase)).get.value()
                println("Succesful Userpost parse and validate")
                //create jwt link
                val event: Event = securedUserPost.toEventWithoutID
                //is this event even still free? (tested in jwt route anyway, but could be done here in addition too)
                onSuccess(database.couldInsert(event)) {
                  case true => {
                    val jwtsubpathlinkEmailConfirm: String = LinkJWTRoute.computeLinkCompletepathForEmailConfirmation(host, securedUserPost.toReservation(host))
                    //send to user to confirm
                    mailer.sendEmailConfirmToUser(jwtsubpathlinkEmailConfirm, event)
                    complete(userresponsetext)
                  }
                  case false => reject
                }

              }
              case Failure(e) => {
                println(s"Failure while parsing Userpost: $e")
                reject
              }
            }
          }
        }
        }
      }
    }
  }

  def superuserpostroute: Route = path(superuserPostPathWithoutSlashes) {
    csrfCheckForSameOrigin { headers =>
      authenticateBasic(passwordConfig.realmForCredentials+ "-adminrealm", Authenticators.onlySuperuserAuthenticator
      (passwordConfig)) { username =>
        post {
          entity(as[String]) { jsonstring =>
            Try(read[SimpleSuperuserPost](jsonstring).sanitizeHTML.validateWithException) match {
              case Success(securedSuperuserPost) => {
                if (securedSuperuserPost.delete.contains(true)) {
                  //delete this event and reject if it does not exist anymore
                  onSuccess(database.delete(securedSuperuserPost.eventID)) {
                    case Some(e) => {
                      //in case of complete, write mail to user
                      mailer.sendDeclinedNotificationToUser(e)
                      complete(s"Event with ID = ${securedSuperuserPost.eventID} was deleted")
                    }
                    case None => reject
                  }
                } else if (securedSuperuserPost.confirm.isDefined) {
                  onSuccess(database.updateConfirmation(securedSuperuserPost.eventID, securedSuperuserPost.confirm.get)) {
                    case Some(event) => {
                      val host : String = headers.find(_.is(XForwardedHostHeader.toLowerCase)).get.value()
                      //in case of complete, write mail to user
                      if (event.confirmedBySupseruser) {
                        mailer.sendConfirmedNotificationToSuperuser(event)
                        mailer.sendConfirmedNotificationToUser(event,
                          LinkJWTRoute.completelinkToDeleteEventFromUserAfterConfirmation(host, event))
                      } else {
                        mailer.sendUnconfirmedNotificationToUser(event)
                      }
                      complete(s"Event with ID = ${securedSuperuserPost.eventID} was ${
                        if (event.confirmedBySupseruser)
                          "confirmed"
                        else "set to " +
                          "unconfirmed"
                      }")
                    }
                    case None => reject
                  }
                } else {
                  reject
                }
              }
              case Failure(e) => {
                reject
              }
            }
          }
        }
      }
    }
  }


}


object PostChangesRoute {

  def userPostPath = "/" + userPostPathWithoutSlashes

  private def userPostPathWithoutSlashes = "newevent"

  def superuserPostPath = "/" + superuserPostPathWithoutSlashes

  private def superuserPostPathWithoutSlashes = "changeevent"

  def userresponsetext = "You have received an email containing a link. Press that link to confirm your email address."


  //CSRF Protection:
  //compare Origin and X-FORWARDED-HOST headers for first CSRF Protection Stage
  //require "X-Requested-With: XMLHttpRequest" to guarantee same origin (as this is a custom header)

  def csrfCheckForSameOrigin(route: scala.collection.immutable.Seq[akka.http.scaladsl.model.HttpHeader] => Route):
  Route = {
    extractRequest { request => {
      if (!equalOiriginAndXForwardedHostHeader(request.headers)) {
        println("incoming post request not equalOiriginAndXForwardedHostHeader, see headers: " + request.headers)
        reject(MissingHeaderRejection(XForwardedHostHeader), MissingHeaderRejection(OriginHeader))
      } else if (!hasXRequestedWith(request.headers)) {
        println("incoming post request not hasXRequestedWith, see headers: " + request.headers)
        reject(MissingHeaderRejection(XRequestedWithHeader))
      } else {
        println("post request passed CSRF check")
        route.apply(request.headers)
      }
    }
    }
  }

  def XForwardedHostHeader = """X-Forwarded-Host"""

  def equalOiriginAndXForwardedHostHeader(seq: Seq[HttpHeader]): Boolean = {
    val origin = seq.find(_.is(OriginHeader.toLowerCase))
    val xforwardedhost = seq.find(_.is(XForwardedHostHeader.toLowerCase))
    if (origin.isDefined && xforwardedhost.isDefined) {
      val a = Try(new URI(origin.get.value().trim())).toOption
      val b = Try(new URI(xforwardedhost.get.value().trim())).toOption
      println(s"origin headers: $a vs $b")
      def defined = a.isDefined && b.isDefined
      def localhost = isLocalhost(a.get) && isLocalhost(b.get)
      def samehost = a.get.getHost.trim.equals(b.get.getHost.trim)
      defined && (localhost || samehost)
      //make java URI class deal with this for us
    } else {
      false
    }
  }

  def OriginHeader = """Origin"""

  def isLocalhost(uri: URI): Boolean = {
    println(s"checking localhost for uri $uri with ascii = ${uri.toASCIIString}")
    /* //this is wrong and commented out for that reason:
    if (uri.getHost != null) {
      println("host not null")
      false
    } else*/ if (uri.toASCIIString.startsWith("http://localhost:")) {
      println("startswith " + "http://localhost:")
      true
    } else if (uri.toASCIIString.startsWith("https://localhost:")) {
      println("startswith " + "https://localhost:")
      true
    } else if (uri.toASCIIString.startsWith("localhost:")) {
      println("startswith " + "localhost:")
      true
    } else {
      println("else")
      false
    }
  }

  def XRequestedWithHeader = """X-Requested-With"""

  def hasXRequestedWith(seq: Seq[HttpHeader]): Boolean = {
    seq.find(_.is(XRequestedWithHeader.toLowerCase())).exists(s => s.value().trim().toLowerCase()
      .equals(XRequestedWithValue.trim().toLowerCase()))
  }

  def XRequestedWithValue = """XMLHttpRequest"""


}
