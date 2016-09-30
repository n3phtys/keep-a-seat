package org.nephtys.keepaseat.internal.linkkeys

import org.nephtys.cmac.{HmacValue, MacSource}
import org.nephtys.keepaseat.Databaseable
import org.nephtys.keepaseat.internal.eventdata.{Event, EventElementBlock}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

/**
  * Created by nephtys on 9/28/16.
  */
sealed trait ConfirmationOrDeletion {

  def randomNumber : Long //to salt the hmacs

  def eventid : Long

  def confirmingThisReservation : Boolean

  /**
    * returns Some(true) if the event was confirmed and Some(false if it was declined. None returns some kind of error
    * @return
    */
  def writeUpdateOrRemoveToDatabase(implicit database: Databaseable) : Future[Option[Boolean]]
}


case class SimpleConfirmationOrDeletion(eventid : Long, confirmingThisReservation : Boolean, randomNumber : Long)
extends
  ConfirmationOrDeletion {
  /**
    * returns Some(true) if the event was confirmed and Some(false if it was declined. None returns some kind of error
    *
    * @return
    */
  override def writeUpdateOrRemoveToDatabase(implicit database: Databaseable): Future[Option[Boolean]] = {
    if (confirmingThisReservation) {
      database.updateConfirmation(eventid, confirmingThisReservation).map(a =>  if (a) {Some(true)} else {
        None
      })
    } else {
      database.delete(eventid).map(a =>  if (a) {Some(false)} else {
        None
      })
    }
  }
}

object ConfirmationOrDeletion {
  import org.nephtys.cmac.HmacHelper._
  import upickle.default._
  def makeUrlencodedJWT(request : ConfirmationOrDeletion)(implicit macSource : MacSource) : String = {
    request.toHMAC().toURLEncodedString().encodedString
  }
  def fromJWTString(urlencodedjwt : String)(implicit macSource : MacSource) : HmacValue[ConfirmationOrDeletion] = {
    urlencodedjwt.fromURLEncodedString.toHMAC[ConfirmationOrDeletion]()
  }
}