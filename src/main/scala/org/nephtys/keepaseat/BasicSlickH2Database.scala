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
class BasicSlickH2Database(db : Database)  extends Databaseable{


  //TODO: implement with slick and embedded h2 and all required code


  override def retrieve(fromDate: Long, toDate: Long): Future[IndexedSeq[Event]] = ???

  override def update(event: Event): Future[Boolean] = ???

  override def create(eventWithoutID: Event): Future[Option[Event]] = ???

  override def delete(id: Long): Future[Boolean] = ???

  override def retrieveSpecific(id: Long): Future[Option[Event]] = ???
}

/**
  * automatically create schema at first call
  */
object BasicSlickH2Database {
  private val ai = new AtomicInteger(1)

  def dbRandomInMemory : Database = {
    val db : Database = Database.forURL("jdbc:h2:mem:keepaseat_inmemory_" + ai.getAndIncrement() + ";DB_CLOSE_DELAY=-1", driver = "org.h2" +
      ".Driver",
      keepAliveConnection = true)
    //db created, now create schema
    Await.result(db.run(DBIO.seq(tableDefinitions.events.schema.create)), Duration.apply(1, "minute"))
    db
  }


  private val dbFac : scala.collection.mutable.Map[String, Database] = mutable.Map.empty[String, Database]
  def dbFromFile(filepath : String) : Database = {
    dbFac.getOrElseUpdate(filepath, {
      val db : Database = Database.forURL("jdbc:h2:" + absolutifyFilepath(filepath) + ";MV_STORE=FALSE;" +
        "DB_CLOSE_DELAY=1", driver = "org.h2" +
        ".Driver",
        keepAliveConnection = true)
      //db created, now create schema if file was non-existing before
      if (Files.notExists(Paths.get(filepath + ".h2.db"))) {
        //TODO: this is actually not a smart solution to this problem. Should be improved
        Await.result(db.run(DBIO.seq(tableDefinitions.events.schema.create)), Duration.apply(1, "minute"))
      }
      db
    })
  }


  def absolutifyFilepath(relativeFilepath : String) : String = {
    new File(relativeFilepath).getAbsolutePath
  }



  object tableDefinitions {
    class EventElementBlocks(tag : Tag) extends Table[(Long, Long, String, Long, Long)](tag,
      "ELEMENTSTOEVENT") {
      def ownid = column[Long]("ELEMENTEVENT_ID", O.PrimaryKey)

      def eventID = column[Long]("EVENT_ID")

      def event = foreignKey("EVENT_FK", eventID, events)(_.id)

      def from = column[Long]("TIME_FROM")
      def to = column[Long]("TIME_TO")

      def elementName = column[String]("ELEMENT_NAME")

      def * = (ownid, eventID, elementName, from, to)

    }


    class Events(tag: Tag) extends Table[(Long, String, String, String, String, Boolean)](tag, "EVENTS") {
      def id = column[Long]("EVENT_ID", O.PrimaryKey) // This is the primary key column
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

  }
}
