package org.nephtys.keepaseat.internal.posts

import org.nephtys.keepaseat.filter.XSSCleaner
import org.nephtys.keepaseat.internal.eventdata.{Event, EventElementBlock}
import org.nephtys.keepaseat.internal.validators.{UserPostValidator, ValidatorFailedException}

/**
  * Created by nephtys on 9/28/16.
  */
sealed trait UserPost {

  def email : String
  def name : String
  def telephone : String
  def commentary : String

  def elements : Seq[EventElementBlock]

  def validate(implicit seq : Seq[UserPostValidator]) : Boolean = seq.forall(a => a.validate(this))

  def validateWithException(implicit seq : Seq[UserPostValidator]) : UserPost = {
    val res = validate
    if (res) {
      this
    } else {
      throw new ValidatorFailedException()
    }
  }

  def toEventWithoutID : Event

  def sanitizeHTML(implicit xSSCleaner: XSSCleaner) :UserPost
}

case class SimpleUserPost(name : String, email : String, telephone : String, commentary : String, elements :
Seq[EventElementBlock])
  extends
  UserPost{
  override def sanitizeHTML(implicit xss: XSSCleaner): UserPost = {
    SimpleUserPost(xss.removeHTML(name), xss.removeHTML(email), xss.removeHTML(telephone), xss.removeHTML(commentary)
      , elements.map(_.cleanHTML))
  }

  override def toEventWithoutID: Event = Event(-1, elements, name, email, telephone, commentary, confirmedBySupseruser = false)
}