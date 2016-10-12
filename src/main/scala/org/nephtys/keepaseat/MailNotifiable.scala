package org.nephtys.keepaseat

import org.nephtys.keepaseat.internal.eventdata.Event

/**
  * Created by nephtys on 9/28/16.
  */
trait MailNotifiable {

  def sendUnconfirmedNotificationToUser(event : Event)

  def sendEmailConfirmToUser(subpathlinkToConfirm: String, event : Event)

  def sendConfirmOrDeclineToSuperuser(event: Event, subpathlinkToConfirm: String, subpathlinkToDecline: String) : Unit


  def sendConfirmedNotificationToSuperuser(event : Event) : Unit

  def sendConfirmedNotificationToUser(event : Event, deleteLink : String) : Unit

  def sendNotYetConfirmedNotificationToUser(event : Event, deleteLink : String) : Unit

  def sendDeclinedNotificationToSuperuser(event : Event) : Unit

  def sendDeclinedNotificationToUser(event : Event) : Unit


}
