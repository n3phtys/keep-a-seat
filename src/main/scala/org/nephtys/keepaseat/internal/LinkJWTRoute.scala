package org.nephtys.keepaseat.internal

import akka.http.scaladsl.server._
import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import org.nephtys.cmac.MacSource
import org.nephtys.cmac.HmacHelper._
import org.nephtys.keepaseat.{Databaseable, MailNotifiable}
import org.nephtys.keepaseat.internal.configs.{Authenticators, PasswordConfig}
import org.nephtys.keepaseat.internal.eventdata.Event
import org.nephtys.keepaseat.internal.linkkeys.{ConfirmationOrDeletion, ReservationRequest, SimpleReservation}

import scala.util.{Failure, Success, Try}

/**
  * Created by nephtys on 9/28/16.
  */
class LinkJWTRoute()(implicit passwordConfig: PasswordConfig, macSource: MacSource, database: Databaseable,
                     mailer: MailNotifiable) {

  import LinkJWTRoute._

  //this does not actually require basic auth, but being safe is always better


  def extractRoute: Route = emailConfirmationRoute ~ superuserConfirmationOrDeclineRoute

  private def emailConfirmationRoute: Route = path(pathToEmailConfirmation) {
    authenticateBasic(passwordConfig.realmForCredentials(), Authenticators.normalUserOrSuperuserAuthenticator
    (passwordConfig)) { username =>
      get {
        parameter('jwt.as[String]) { urlencodedjwt => {
          Try({
            ReservationRequest.fromJWTString(urlencodedjwt)
          }) match {
            case Success(reservation) => {
              val event = reservation.t.toNewEventWithoutID
              onSuccess(database.create(event)) {
                case Some(ev) => {
                  //send emails
                  val encodedjwts: Seq[String] = Seq(true, false).map(b => ConfirmationOrDeletion.fromForSuperuser(ev, b)
                    .toUrlencodedJWT)
                  val subpathlinks: Seq[String] = encodedjwts.map(computeLinkSubpathForSuperuserConfirmation)
                  mailer.sendConfirmOrDeclineToSuperuser(subpathlinks.head, subpathlinks.last)
                  mailer.sendNotYetConfirmedNotificationToUser(ev, subpathlinkToDeleteEventFromUserAfterConfirmation(ev))
                  complete(emailConfirmSuccessText)
                }
                case _ => {
                  complete(emailConfirmFailureText)
                }
              }
            }
            case Failure(e) => reject(akka.http.scaladsl.server.MalformedQueryParamRejection("jwt", "jwt for email " +
              "confirm" +
              " " +
              s"unparsable${if(isDebug) " with Exception: "+e}"))
          }


        }
        }

      }
    }
  }


  //should not allow deletes by user after the fact
  private def superuserConfirmationOrDeclineRoute: Route = path(pathToSuperuserConfirmation) {
    authenticateBasic(passwordConfig.realmForCredentials(), Authenticators.normalUserOrSuperuserAuthenticator
    (passwordConfig)) { username => //normal users can use this path too via their delete link.
      get {
        parameter('jwt.as[String]) { urlencodedjwt => {
          try {
            val confirmation = ConfirmationOrDeletion.fromJWTString(urlencodedjwt).t
            onSuccess(confirmation.writeUpdateOrRemoveToDatabase) {
              case Some((true, event)) => {
                mailer.sendConfirmedNotificationToSuperuser(event)
                mailer.sendConfirmedNotificationToUser(event, subpathlinkToDeleteEventFromUserAfterConfirmation(event))
                complete(confirmReservationText)
              }
              case Some((false, event)) => {
                mailer.sendDeclinedNotificationToSuperuser(event)
                mailer.sendDeclinedNotificationToUser(event)
                complete("You declined the given reservation. Everyone will receive a notification email about " +
                  "the change.")
              }
              case None => {
                reject
              }
            }
          } catch {
            case e : Exception => reject(akka.http.scaladsl.server.MalformedQueryParamRejection("jwt", "jwt " +
              "unparsable for super confirm or decline"))
          }

        }
        }

      }
    }
  }
}

object LinkJWTRoute {

  def isDebug = true



  val pathToEmailConfirmation: String = "confirmemail"
  val pathToSuperuserConfirmation: String = "confirmreservation"


  val emailConfirmSuccessText : String = "Your Reservation was successfully registered and your email address confirmed. The Administrator " +
    "will be " +
    "presented with your registration, and confirm or decline it soon. You will be notified by email " +
    "afterwards" +
    " " +
    "in either " +
    "case. Thank you for your understanding!"

  val confirmReservationText : String = "You accepted the given reservation. You and the user will receive a notification email about " +
    "the change."

  val emailConfirmFailureText : String = "Sorry, but your reservation could not be made, as someone other has blocked the alloted " +
    "timeslots in the meanwhile. Please try another reservation."

  def subpathlinkToDeleteEventFromUserAfterConfirmation(event : Event)(implicit macSource: MacSource) : String =  {
    "/"+pathToSuperuserConfirmation + "?jwt=" + ConfirmationOrDeletion.fromForUser(event).toUrlencodedJWT
  }




  def computeLinkSubpathForEmailConfirmation(urlencodedjwt: String): String = "/"+pathToEmailConfirmation +
    "?jwt=" +
    urlencodedjwt

  private def computeLinkSubpathForEmailConfirmation(event : Event)(implicit macSource: MacSource): String = { //is
    // this ever needed?
    computeLinkSubpathForEmailConfirmation(event.toHMAC().toURLEncodedString().encodedString)
  }
  def computeLinkSubpathForEmailConfirmation(reservationRequest: SimpleReservation)(implicit macSource: MacSource): String = {
    computeLinkSubpathForEmailConfirmation(reservationRequest.toHMAC().toURLEncodedString().encodedString)
  }


  def computeLinkSubpathForSuperuserConfirmation(urlencodedjwt: String): String = "/"+pathToSuperuserConfirmation + "?jwt=" + urlencodedjwt
}
