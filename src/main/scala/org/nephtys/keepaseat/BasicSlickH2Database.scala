package org.nephtys.keepaseat

import java.io.File
import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicInteger

import org.nephtys.keepaseat.internal.eventdata.{Event, EventElementBlock}
import slick.lifted.ProvenShape

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.Duration

// Use H2Driver to connect to an H2 database
import slick.driver.H2Driver.api._

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

/**
  * simple implementation of the Databaseable trait, using an embedded h2 database (file based) and synchronization
  * features
  *
  * Created by nephtys on 9/29/16.
  */
class BasicSlickH2Database(db: Database) extends Databaseable {


  //TODO: implement with slick and embedded h2 and all required code


  override def retrieve(fromDate: Long, toDate: Long): Future[IndexedSeq[Event]] = db.run(BasicSlickH2Database
    .queries.getBetween(fromDate, toDate)
    .result).map(_.groupBy(_._1).map(a => {
    def eventraw = a._1
    def blocksraw = a._2.map(_._2).toIndexedSeq
    val blocks: IndexedSeq[EventElementBlock] = blocksraw.map(p => EventElementBlock(p._3, p._4, p._5))
    Event(eventraw._1, blocks, eventraw._2, eventraw._3, eventraw._4, eventraw._5, eventraw._6)
  }).toIndexedSeq)

  override def update(event: Event): Future[Option[Event]] = {
    retrieveSpecific(event.id).map(_.isDefined).flatMap(b => if (b) {
      val updateBlocks = db.run(BasicSlickH2Database.queries.updateBlocks(event.id, event.elements))
      val updateEvent = db.run(BasicSlickH2Database.queries.updateEvent(event))
      val fseq: Future[_] = Future.sequence(Seq(updateBlocks, updateEvent))
      fseq.map(a => Some(event))
    } else {
      Future.successful(None)
    })
  }

  override def create(eventWithoutID: Event): Future[Option[Event]] = {
    //TODO: this should be done in one single DBIOAction to prevent multi threading issues

    val from = eventWithoutID.elements.map(_.from).min
    val to = eventWithoutID.elements.map(_.to).max
    val free: Future[Boolean] = retrieve(from, to).map(_.isEmpty)
    val eventinsert: Future[(Boolean, Long)] = free.flatMap(b => if (b) {
      db.run(BasicSlickH2Database.queries
        .insertEvent
        (eventWithoutID)).map(i => (b, i))
    } else {
      Future.successful(b, 0.asInstanceOf[Long])
    })
    val blockInsert: Future[(Boolean, Long)] = eventinsert.flatMap(tuple => {
      if (tuple._1) {
        db.run(BasicSlickH2Database.queries.insertBlocks(tuple._2, eventWithoutID.elements)).map(a => tuple)
      } else {
        Future.successful(tuple)
      }
    })
    blockInsert.map {
      case (true, eid) => Some(eventWithoutID.copy(id = eid))
      case _ => None
    }
  }

  override def delete(id: Long): Future[Option[Event]] = {
    retrieveSpecific(id).flatMap(b => {
      if (b.isDefined) {
        val deleteBlocks: Future[Int] = db.run(BasicSlickH2Database.queries.removeBlocks(id))
        val deleteEvent: Future[Int] = db.run(BasicSlickH2Database.queries.removeElement(id))
        Future.sequence(Seq(deleteBlocks, deleteEvent)).map(i =>  b)
      } else {
        Future.successful(b)
      }
    })
  }

  override def retrieveSpecific(id: Long): Future[Option[Event]] = db.run(BasicSlickH2Database.queries.getWithID(id)
    .result).map(_.groupBy(_._1).headOption.map(a => {
    def eventraw = a._1
    def blocksraw = a._2.map(_._2).toIndexedSeq
    val blocks: IndexedSeq[EventElementBlock] = blocksraw.map(p => EventElementBlock(p._3, p._4, p._5))
    Event(eventraw._1, blocks, eventraw._2, eventraw._3, eventraw._4, eventraw._5, eventraw._6)
  }))

  /**
    * return true if update was successful
    *
    * @param eventID
    * @param confirmstatus
    * @return
    */
  override def updateConfirmation(eventID: Long, confirmstatus: Boolean): Future[Option[Event]] = ??? //TODO: implement
}

/**
  * automatically create schema at first call
  */
object BasicSlickH2Database {
  private val ai = new AtomicInteger(1)

  def dbRandomInMemory: Database = {
    val db: Database = Database.forURL("jdbc:h2:mem:keepaseat_inmemory_" + ai.getAndIncrement() + ";DB_CLOSE_DELAY=-1", driver = "org.h2" +
      ".Driver",
      keepAliveConnection = true)
    //db created, now create schema
    Await.result(db.run(DBIO.seq(tableDefinitions.events.schema.create)), Duration.apply(1, "minute"))
    db
  }


  private val dbFac: scala.collection.mutable.Map[String, Database] = mutable.Map.empty[String, Database]

  def dbFromFile(filepath: String): Database = {
    dbFac.getOrElseUpdate(filepath, {
      val db: Database = Database.forURL("jdbc:h2:" + absolutifyFilepath(filepath) + ";MV_STORE=FALSE;" +
        "DB_CLOSE_DELAY=1", driver = "org.h2" +
        ".Driver",
        keepAliveConnection = true)
      //db created, now create schema if file was non-existing before
      if (Files.notExists(Paths.get(filepath + ".h2.db"))) {
        //TODO: this is actually not a smart solution to this problem. Should be improved
        Await.result(db.run(DBIO.seq((tableDefinitions.events.schema ++ tableDefinitions.blocks.schema).create)),
          Duration.apply(1, "minute"))
      }
      db
    })
  }


  def absolutifyFilepath(relativeFilepath: String): String = {
    new File(relativeFilepath).getAbsolutePath
  }


  object tableDefinitions {

    class EventElementBlocks(tag: Tag) extends Table[(Long, Long, String, Long, Long)](tag,
      "ELEMENTSTOEVENT") {
      def ownid = column[Long]("ELEMENTEVENT_ID", O.PrimaryKey)

      def eventID = column[Long]("EVENT_ID")

      def event = foreignKey("EVENT_FK", eventID, events)(_.id)

      def from = column[Long]("TIME_FROM")

      def to = column[Long]("TIME_TO")

      def elementName = column[String]("ELEMENT_NAME")

      def * = (ownid, eventID, elementName, from, to)

    }

    val blocks = TableQuery[EventElementBlocks]


    class Events(tag: Tag) extends Table[(Long, String, String, String, String, Boolean)](tag, "EVENTS") {
      def id = column[Long]("EVENT_ID", O.PrimaryKey)

      // This is the primary key column
      def name = column[String]("USER_NAME")

      def email = column[String]("EMAIL")

      def telephone = column[String]("TELEPHONE")

      def commentary = column[String]("COMMENTARY")

      def confirmedBySupseruser = column[Boolean]("CONFIRMED")

      // Every table needs a * projection with the same type as the table's type parameter
      def * = (id, name, email, telephone, commentary, confirmedBySupseruser)
    }

    val events = TableQuery[Events]

  }

  object queries {

    def getWithID(lid: Long) = tableDefinitions.events.join(tableDefinitions.blocks).on(_.id === _.ownid).filter(_
      ._1.id === lid)

    def getBetween(from: Long, to: Long) = tableDefinitions.events.join(tableDefinitions.blocks).on(_
      .id === _
      .ownid).filterNot(b => b._2.from > to || b._2.to < from)


    def insertEvent(event: Event) = (tableDefinitions.events returning tableDefinitions.events.map(_.id)) +=
      (-1.asInstanceOf[Long], event.name, event.email, event.telephone, event.commentary, event.confirmedBySupseruser)

    def insertBlocks(eventId: Long, blocks: Seq[EventElementBlock]) = tableDefinitions.blocks ++= blocks.map(block =>
      (-1.asInstanceOf[Long], eventId, block.element, block.from, block.to))


    def removeElement(elementId: Long) = {
      val q = tableDefinitions.events.filter(_.id === elementId)
      val action = q.delete
      action
    }

    def removeBlocks(elementId: Long) = {
      val q = tableDefinitions.blocks.filter(_.eventID === elementId)
      val action = q.delete
      action
    }

    def tupleWithOutID(e: Event): (String, String, String, String, Boolean) = (e.name, e.email, e.telephone, e
      .commentary, e.confirmedBySupseruser)

    def updateEvent(updatedElement: Event) = {
      val q = tableDefinitions.events.filter(_.id === updatedElement.id).map(e => {
        (e.name, e.email, e.telephone, e.commentary, e.confirmedBySupseruser)
      }).update(tupleWithOutID(updatedElement))
      q
    }

    /** just reset them all, it's better this way
      *
      * @param elemntid
      * @param blocks
      * @return
      */
    def updateBlocks(elemntid: Long, blocks: Seq[EventElementBlock]) = DBIO.seq(removeBlocks(elemntid),
      insertBlocks(elemntid, blocks))
  }

}
