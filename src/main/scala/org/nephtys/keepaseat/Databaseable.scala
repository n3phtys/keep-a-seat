package org.nephtys.keepaseat

import org.nephtys.keepaseat.internal.eventdata.{Event, EventElementBlock}

import scala.concurrent.Future

/**
  * Created by nephtys on 9/28/16.
  */
trait Databaseable {
  def couldInsert(event: Event) : Future[Boolean]

  def retrieve(fromInclusiveDate : Long = 0, toExclusiveDate : Long = Long.MaxValue) : Future[IndexedSeq[Event]]
  def retrieveSpecific(id : Long) : Future[Option[Event]]
  def update(event : Event) : Future[Option[Event]]
  def create(eventWithoutID : Event) : Future[Option[Event]] //can return NONE if not finding any place in the DB
  def delete(id : Long) : Future[Option[Event]]

  /**
    * return true if update was successful
    * @param eventID
    * @param confirmstatus
    * @return
    */
  def updateConfirmation(eventID : Long, confirmstatus : Boolean) : Future[Option[Event]]

}

object Databaseable {

  def intersect(inclusiveAbegin: Long, inclusiveBbegin: Long, exclusiveAend: Long, exclusiveBend : Long) : Boolean = {
    assert(inclusiveAbegin < exclusiveAend)
    assert(inclusiveBbegin < exclusiveBend)

        if (inclusiveAbegin >= exclusiveBend) {
          //A comes after B completely
          false
        } else if (inclusiveBbegin >= exclusiveAend) {
          //B comes after A completely
          false
        } else {
          //they somehow intersect
          true
        }
  }
}