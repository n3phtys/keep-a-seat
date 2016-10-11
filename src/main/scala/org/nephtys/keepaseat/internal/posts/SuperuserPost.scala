package org.nephtys.keepaseat.internal.posts

import org.nephtys.keepaseat.filter.XSSCleaner
import org.nephtys.keepaseat.internal.validators.{SuperuserPostValidator, UserPostValidator, ValidatorFailedException}

/**
  * Created by nephtys on 9/28/16.
  */
trait SuperuserPost {

  def eventID : Long

  def delete : Option[Boolean]

  def confirm : Option[Boolean]


  def validate(implicit seq : Seq[SuperuserPostValidator]) : Boolean = seq.forall(a => a.validate(this))


  def validateWithException(implicit seq : Seq[SuperuserPostValidator]) : SuperuserPost = {
    val res = validate
    if (res) {
      this
    } else {
      throw new ValidatorFailedException()
    }
  }


  def sanitizeHTML(implicit xSSCleaner: XSSCleaner) :SuperuserPost
}

case class SimpleSuperuserPost(eventID: Long, delete: Option[Boolean], confirm: Option[Boolean]) extends SuperuserPost {

  /**
    * does not require sanitation, because no strings involved
    * @param xSSCleaner
    * @return
    */
  override def sanitizeHTML(implicit xSSCleaner: XSSCleaner): SuperuserPost = this
}