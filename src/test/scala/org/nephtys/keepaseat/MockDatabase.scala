package org.nephtys.keepaseat
import java.util.concurrent.atomic.AtomicLong

import org.nephtys.keepaseat.internal.eventdata.Event

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by nephtys on 9/28/16.
  */
class MockDatabase extends Databaseable {

  private val db : scala.collection.concurrent.TrieMap[Long, Event] = scala.collection.concurrent.TrieMap
    .empty[Long,
    Event]

  private val idSource = new AtomicLong(1)

  override def retrieve(fromDate: Long, toDate: Long): Future[IndexedSeq[Event]] = Future.successful(db.values.filter(event => {
    event.elements.exists(b => Databaseable.intersect(fromDate, b.from, toDate, b.to))
  }).toIndexedSeq)

  override def update(event: Event): Future[Boolean] = Future.successful({
    if (db.contains(event.id)) {
      db.update(event.id, event)
      true
    } else {
      false
    }
  })

  def getUnconfirmedEventID : Long = db.filterNot(_._2.confirmedBySupseruser).keys.min

  override def create(eventWithoutID: Event): Future[Option[Event]] = {
    val from  = eventWithoutID.elements.map(_.from).min
    val to    = eventWithoutID.elements.map(_.to).max
    retrieve(from, to).map( indexedseq => {
      if (indexedseq.isEmpty) {
        val newid = idSource.getAndIncrement()
        val eventWithID = eventWithoutID.copy(id = newid)
        db.put(newid, eventWithID)
        Some(eventWithID)
      } else  {
        None
      }
    }
    )
  }

  override def delete(id: Long): Future[Boolean] = {
    retrieveSpecific(id).map(opt => {
      if (opt.isDefined) {
        db.remove(id)
        true
      } else {
        false
      }
    })
  }

  def clearDatabase() : Unit =  {
    this.db.clear()
  }

  override def retrieveSpecific(id: Long): Future[Option[Event]] = Future.successful(db.get(id))

  override def updateConfirmation(eventID: Long, confirmstatus: Boolean): Future[Boolean] = {
    retrieveSpecific(eventID).map((p : Option[Event]) => p match {
      case Some(event) => {
        db.update(eventID, event.copy(confirmedBySupseruser = confirmstatus))
        true
      }
      case None => false
    })
  }
}
