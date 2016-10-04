package org.nephtys.keepaseat.internal

import akka.http.scaladsl.server._
import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import org.nephtys.cmac.MacSource
import org.nephtys.keepaseat.{Databaseable, MailNotifiable}
import org.nephtys.keepaseat.filter.XSSCleaner
import org.nephtys.keepaseat.internal.configs.{Authenticators, PasswordConfig}
import org.nephtys.keepaseat.internal.eventdata.Event
import org.nephtys.keepaseat.internal.posts.{SimpleSuperuserPost, SimpleUserPost, SuperuserPost, UserPost}
import org.nephtys.keepaseat.internal.validators.{SuperuserPostValidator, UserPostValidator}
import upickle.default._

import scala.util.{Failure, Success, Try}

/**
  * Created by nephtys on 9/28/16.
  */
class PostChangesRoute(implicit passwordConfig : () => PasswordConfig, mailer : MailNotifiable,
                      database : Databaseable, macSource : MacSource,
                       xssCleaner : XSSCleaner, validatorsUser : Seq[UserPostValidator], validatorsSuperuser :
                       Seq[SuperuserPostValidator]) {

  //TODO: 2 x 2 tests for this route

  private val userPostPathWithoutSlashes = "newevent"
  val userPostPath = "/"+userPostPathWithoutSlashes

  private val superuserPostPathWithoutSlashes = "changeevent"
  val superuserPostPath = "/"+superuserPostPathWithoutSlashes

  val userresponsetext = "You have received an email containing a link. Press that link to confirm your email address."


  def userpostroute = path(userPostPathWithoutSlashes) {
    authenticateBasic(passwordConfig.apply().realmForCredentials(), Authenticators.normalUserOrSuperuserAuthenticator
    (passwordConfig)) { username =>
      post {
        entity(as[String]) { jsonstring =>
          Try(read[SimpleUserPost](jsonstring).sanitizeHTML.validateWithException) match {
            case Success(securedUserPost) => {
              //create jwt link
              val event : Event = securedUserPost.toEventWithoutID
              //is this event even still free? (tested in jwt route anyway, but could be done here in addition too)
              onSuccess(database.couldInsert(event)) {
                case true => {
                  val jwtsubpathlinkEmailConfirm : String = LinkJWTRoute.computeLinkSubpathForEmailConfirmation(securedUserPost.toReservation)
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


  def superuserpostroute : Route = path(superuserPostPathWithoutSlashes) {
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
                    complete(s"Event with ID = ${securedSuperuserPost.eventID} was ${if(event.confirmedBySupseruser)
                      "confirmed" else "set to " +
                      "unconfirmed" }")
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


  def extractRoute : Route = userpostroute ~ superuserpostroute


}
