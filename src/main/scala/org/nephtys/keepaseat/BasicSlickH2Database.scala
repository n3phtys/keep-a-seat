package org.nephtys.keepaseat
import org.nephtys.keepaseat.internal.eventdata.Event

import scala.concurrent.Future

/**
  * simple implementation of the Databaseable trait, using an embedded h2 database (file based) and synchronization
  * features
  *
  * Created by nephtys on 9/29/16.
  */
class BasicSlickH2Database(dbString : String)  extends Databaseable{
  //TODO: implement with slick and embedded h2 and all required code


  override def retrieve(fromDate: Long, toDate: Long): Future[IndexedSeq[Event]] = ???

  override def update(event: Event): Future[Boolean] = ???

  override def create(eventWithoutID: Event): Future[Event] = ???

  override def delete(id: Long): Future[Boolean] = ???
}
