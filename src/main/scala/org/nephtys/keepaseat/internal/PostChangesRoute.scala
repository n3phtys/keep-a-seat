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
class PostChangesRoute(implicit passwordConfig: () => PasswordConfig, mailer: MailNotifiable,
                       database: Databaseable, macSource: MacSource,
                       xssCleaner: XSSCleaner, validatorsUser: Seq[UserPostValidator], validatorsSuperuser:
                       Seq[SuperuserPostValidator]) {

  import PostChangesRoute._


  def extractRoute: Route = userpostroute ~ superuserpostroute

  def userpostroute = path(userPostPathWithoutSlashes) {
    csrfCheckForSameOrigin {
      authenticateBasic(passwordConfig.apply().realmForCredentials(), Authenticators.normalUserOrSuperuserAuthenticator
      (passwordConfig)) { username =>
        post {
          entity(as[String]) { jsonstring =>
            Try(read[SimpleUserPost](jsonstring).sanitizeHTML.validateWithException) match {
              case Success(securedUserPost) => {
                //create jwt link
                val event: Event = securedUserPost.toEventWithoutID
                //is this event even still free? (tested in jwt route anyway, but could be done here in addition too)
                onSuccess(database.couldInsert(event)) {
                  case true => {
                    val jwtsubpathlinkEmailConfirm: String = LinkJWTRoute.computeLinkSubpathForEmailConfirmation(securedUserPost.toReservation)
                    //send to user to confirm
                    mailer.sendEmailConfirmToUser(jwtsubpathlinkEmailConfirm, event)
                    complete(userresponsetext)
                  }
                  case false => reject
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

  def superuserpostroute: Route = path(superuserPostPathWithoutSlashes) {
    csrfCheckForSameOrigin {
      authenticateBasic(passwordConfig.apply().realmForCredentials(), Authenticators.onlySuperuserAuthenticator
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
                      //in case of complete, write mail to user
                      if (event.confirmedBySupseruser) {
                        mailer.sendConfirmedNotificationToSuperuser(event)
                        mailer.sendConfirmedNotificationToUser(event,
                          LinkJWTRoute.subpathlinkToDeleteEventFromUserAfterConfirmation(event))
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

  def csrfCheckForSameOrigin(route: Route): Route = {
    extractRequest { request => {
      if (!equalOiriginAndXForwardedHostHeader(request.headers)) {
        reject(MissingHeaderRejection(XForwardedHostHeader), MissingHeaderRejection(OriginHeader))
      } else if (!hasXRequestedWith(request.headers)) {
        reject(MissingHeaderRejection(XRequestedWithHeader))
      } else {
        route
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
      a.isDefined && b.isDefined && ((isLocalhost(a.get) && isLocalhost(b.get)) || a.get.getHost.trim.equals(b.get
        .getHost.trim))
      //make java URI class deal with this for us
    } else {
      false
    }
  }

  def OriginHeader = """Origin"""

  def isLocalhost(uri: URI): Boolean = {
    if (uri.getHost != null) {
      false
    } else if (uri.toASCIIString.startsWith("http://localhost:")) {
      true
    } else if (uri.toASCIIString.startsWith("https://localhost:")) {
      true
    } else if (uri.toASCIIString.startsWith("localhost:")) {
      true
    } else {
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
