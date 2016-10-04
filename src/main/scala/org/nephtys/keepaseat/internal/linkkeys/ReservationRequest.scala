package org.nephtys.keepaseat.internal.linkkeys

import org.nephtys.cmac.{HmacValue, MacSource}
import org.nephtys.keepaseat.internal.eventdata.{Event, EventElementBlock}

import scala.util.Random

/**
  * Created by nephtys on 9/28/16.
  */
sealed trait ReservationRequest {


  def elements : Seq[EventElementBlock]
  // values
  def name : String
  def email : String
  def telephone : String
  def commentary : String

  def randomNumber : Long //to salt the hmacs

  def toNewEventWithoutID : Event

  def toURLencodedJWT()(implicit macSource : MacSource) = ReservationRequest.makeUrlencodedJWT(this)
}

case class SimpleReservation(elements : Seq[EventElementBlock],
                             name : String,
                             email : String,
                             telephone : String,
                             commentary : String, randomNumber : Long) extends ReservationRequest{
  def toNewEventWithoutID : Event = Event(-1l, elements, name, email, telephone, commentary, confirmedBySupseruser = false)
}


object ReservationRequest {
  import org.nephtys.cmac.HmacHelper._
  import upickle.default._
  def makeUrlencodedJWT(request : ReservationRequest)(implicit macSource : MacSource) : String = {
      request.toHMAC().toURLEncodedString().encodedString
  }
  def fromJWTString(urlencodedjwt : String)(implicit macSource : MacSource) : HmacValue[ReservationRequest] = {
    urlencodedjwt.fromURLEncodedString.toHMAC[ReservationRequest]()
  }

  //TODO: make real random, not this java.util.random crap
  def randomNumber : Long = new Random().nextLong()

}