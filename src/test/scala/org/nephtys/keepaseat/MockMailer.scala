package org.nephtys.keepaseat
import org.nephtys.keepaseat.MockMailer.Notification
import org.nephtys.keepaseat.internal.eventdata.Event

import scala.collection.mutable

/**
  * Created by nephtys on 9/28/16.
  */
class MockMailer extends MailNotifiable{

  val notifications : mutable.Buffer[Notification] = mutable.Buffer.empty[Notification]


  override def sendEmailConfirmToUser(subpathlinkToConfirm: String, event: Event): Unit = notifications +=
    Notification(false, false, false, true, Seq(subpathlinkToConfirm), Some(event))

  override def sendConfirmOrDeclineToSuperuser(subpathlinkToConfirm: String, subpathlinkToDecline: String): Unit = notifications +=
    Notification(true, false, false, false, Seq(subpathlinkToConfirm, subpathlinkToDecline), None)

  override def sendConfirmedNotificationToSuperuser(event: Event): Unit = notifications +=
    Notification(true, false, true, false, Seq.empty, Some(event))

  override def sendConfirmedNotificationToUser(event: Event, deleteLink: String): Unit = notifications +=
    Notification(false, false, true, false, Seq(deleteLink), Some(event))

  override def sendDeclinedNotificationToSuperuser(event: Event): Unit = notifications +=
    Notification(true, true, false, false, Seq.empty, Some(event))

  override def sendDeclinedNotificationToUser(event: Event): Unit = notifications +=
    Notification(false, true, false, false, Seq.empty, Some(event))
}

object MockMailer {
  case class Notification(toSuperuserInsteadOfUser : Boolean, deleteConfirmation : Boolean, confirmConfirmation :
  Boolean,
                               emailConfirmation : Boolean,
                               links : Seq[String],
                               event : Option[Event])

}