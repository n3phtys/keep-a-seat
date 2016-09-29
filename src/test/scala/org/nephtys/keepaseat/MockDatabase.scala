package org.nephtys.keepaseat
import org.nephtys.keepaseat.internal.eventdata.Event

import scala.concurrent.Future

/**
  * Created by nephtys on 9/28/16.
  */
class MockDatabase extends Databaseable {

  //TODO: implement with mutable (threadsafe?) map and returning Future.successful from it

  override def retrieve(fromDate: Long, toDate: Long): Future[IndexedSeq[Event]] = ???

  override def update(event: Event): Future[Boolean] = ???

  override def create(eventWithoutID: Event): Future[Event] = ???

  override def delete(id: Long): Future[Boolean] = ???

  override def retrieveSpecific(id: Long): Future[Option[Event]] = ???
}
