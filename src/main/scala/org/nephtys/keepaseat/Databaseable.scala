package org.nephtys.keepaseat

import org.nephtys.keepaseat.internal.eventdata.Event

import scala.concurrent.Future

/**
  * Created by nephtys on 9/28/16.
  */
trait Databaseable {

  def retrieve(fromDate : Long = 0, toDate : Long = Long.MaxValue) : Future[IndexedSeq[Event]]
  def update(event : Event) : Future[Boolean]
  def create(eventWithoutID : Event) : Future[Event]
  def delete(id : Long) : Future[Boolean]
}
