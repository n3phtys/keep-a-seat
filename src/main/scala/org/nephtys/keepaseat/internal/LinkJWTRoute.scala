package org.nephtys.keepaseat.internal

import akka.http.scaladsl.server._
import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import org.nephtys.cmac.MacSource
import org.nephtys.keepaseat.{Databaseable, MailNotifiable}
import org.nephtys.keepaseat.internal.configs.{Authenticators, PasswordConfig}
import org.nephtys.keepaseat.internal.linkkeys.{ConfirmationOrDeletion, ReservationRequest}

/**
  * Created by nephtys on 9/28/16.
  */
class LinkJWTRoute()(implicit passwordConfig: () => PasswordConfig, macSource: MacSource, database: Databaseable,
                     emailNotifier: MailNotifiable) {

  //this does not require basic auth, but being safe is always better

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

  def extractRoute: Route = emailConfirmationRoute ~ superuserConfirmationOrDeclineRoute


  def computeLinkSubpathForEmailConfirmation(urlencodedjwt: String): String = "/"+pathToEmailConfirmation + "?jwt=" +
  urlencodedjwt

  def computeLinkSubpathForSuperuserConfirmation(urlencodedjwt: String): String = "/"+pathToSuperuserConfirmation + "?jwt=" + urlencodedjwt


  private def emailConfirmationRoute: Route = path(pathToEmailConfirmation) {
    authenticateBasic(passwordConfig.apply().realmForCredentials(), Authenticators.normalUserOrSuperuserAuthenticator
    (passwordConfig)) { username =>
      get {
        parameter('jwt.as[String]) { urlencodedjwt => {
          val reservation = ReservationRequest.fromJWTString(urlencodedjwt).t
          val event = reservation.toNewEventWithoutID
          onSuccess(database.create(event)) {
            case Some(ev) => {
              //TODO: send emails
              complete(emailConfirmSuccessText)
            }
            case _ => {
              complete(emailConfirmFailureText)
            }
          }

        }
        }

      }
    }
  }

  private def superuserConfirmationOrDeclineRoute: Route = path(pathToSuperuserConfirmation) {
    authenticateBasic(passwordConfig.apply().realmForCredentials(), Authenticators.onlySuperuserAuthenticator
    (passwordConfig)) { username =>
      get {
        parameter('jwt.as[String]) { urlencodedjwt => {
          val confirmation = ConfirmationOrDeletion.fromJWTString(urlencodedjwt).t
          onSuccess(confirmation.writeUpdateOrRemoveToDatabase) {
            case Some(true) => {
              //TODO: send emails
              complete(confirmReservationText)
            }
            case Some(false) => {
              //TODO: send emails
              complete("You declined the given reservation. You and the user will receive a notification email about " +
                "the change.")
            }
            case None => {
              reject
            }
          }

        }
        }

      }
    }
  }
}
