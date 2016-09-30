package org.nephtys.keepaseat.internal.linkkeys

import org.nephtys.cmac.{HmacValue, MacSource}
import org.nephtys.keepaseat.Databaseable
import org.nephtys.keepaseat.internal.eventdata.{Event, EventElementBlock}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

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
  def writeUpdateOrRemoveToDatabase(implicit database: Databaseable) : Future[Option[(Boolean, Event)]]


  def toUrlencodedJWT(implicit macSource : MacSource) : String
}


case class SimpleConfirmationOrDeletion(eventid : Long, confirmingThisReservation : Boolean, randomNumber : Long)
extends
  ConfirmationOrDeletion {
  /**
    * returns Some(true, event) if the event was confirmed and Some(false, event) if it was declined. None returns some kind
    * of error
    *
    * @return
    */
  override def writeUpdateOrRemoveToDatabase(implicit database: Databaseable): Future[Option[(Boolean, Event)]] = {
    if (confirmingThisReservation) {
      database.updateConfirmation(eventid, confirmingThisReservation).map(a =>  if (a.isDefined) {Some((true, a.get))}
      else {
        None
      })
    } else {
      database.delete(eventid).map(a =>  if (a.isDefined) {Some((false, a.get))} else {
        None
      })
    }
  }

  override def toUrlencodedJWT(implicit macSource: MacSource): String = ConfirmationOrDeletion.makeUrlencodedJWT(this)
}

/**
  *
  * @param eventid
  * @param randomNumber
  */
case class DeleteFromUserAfterCreation(eventid : Long, randomNumber: Long) extends
  ConfirmationOrDeletion {
  override def confirmingThisReservation: Boolean = false

  /**
    * returns Some(true) if the event was confirmed and Some(false if it was declined. None returns some kind of error
    *
    * @return
    */
  override def writeUpdateOrRemoveToDatabase(implicit database: Databaseable): Future[Option[(Boolean, Event)]] = {
      database.delete(eventid).map(a =>  if (a.isDefined) {Some((false, a.get))} else {
        None
      })
  }

  override def toUrlencodedJWT(implicit macSource: MacSource): String = ConfirmationOrDeletion.makeUrlencodedJWT(this)
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


  val seed = 42
  val random = new Random(seed)
  def randomNumber : Long = {
    //TODO: make really random, enough to be considered secure enough
    random.nextLong()
  }

  def fromForSuperuser(event : Event, confirm : Boolean) : ConfirmationOrDeletion = SimpleConfirmationOrDeletion(event
    .id,
    confirmingThisReservation = confirm, randomNumber)


  def fromForUser(event : Event) : ConfirmationOrDeletion = DeleteFromUserAfterCreation(event.id, randomNumber)
}