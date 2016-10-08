package org.nephtys.keepaseat

import org.nephtys.keepaseat.internal.eventdata.{Event, EventElementBlock}
import org.nephtys.keepaseat.internal.testmocks.MockDatabase
import org.scalatest._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

/**
  * Created by nephtys on 10/4/16.
  */
class DatabaseSpec extends FlatSpec with Matchers {


  def block[T](f : Future[T]) = Await.result(f, Duration(10, "seconds"))

  val availableItems = Seq("Bed A", "Bed B")

  val dbIs : Databaseable = new BasicSlickH2Database(BasicSlickH2Database.dbRandomInMemory)
  val dbShould : Databaseable = new MockDatabase


  //test that BasicSlickDB in memory-mode works exactly like MockDatabase

  val eventToInsert = Event(-1, Seq(EventElementBlock(availableItems.head, 1000, 3000)), "firstname", "first@email",
    "00532",
    "some comment", confirmedBySupseruser = false)

  val blockingEventToInsert = Event(-1, Seq(EventElementBlock(availableItems.head, 500, 1500)), "other dude",
    "other@email",
    "00532",
    "some comment", confirmedBySupseruser = false)
  val newemail = """second@mail"""


  println("Inserting Items")
  "A SlickDatabase" should "allow inserts on empty db" in {
    block(dbIs.couldInsert(eventToInsert)) shouldEqual true
    block(dbShould.couldInsert(eventToInsert)) shouldEqual true
    block(dbIs.create(eventToInsert)).get.equalExceptID(eventToInsert) shouldEqual true
    block(dbShould.create(eventToInsert)).get.equalExceptID(eventToInsert) shouldEqual true
  }

  var retreivedListFirstIs : Seq[Event] = _
  var retreivedListFirstShould : Seq[Event] = _



  it should "retreive the inserted item with a new id" in {
    retreivedListFirstIs  = block(dbIs.retrieve())
    retreivedListFirstShould = block(dbShould.retrieve())

    retreivedListFirstIs.size shouldEqual 1
    retreivedListFirstShould.size shouldEqual 1
    retreivedListFirstIs.head.equalExceptID(eventToInsert) shouldEqual true
    retreivedListFirstShould.head.equalExceptID(eventToInsert) shouldEqual true
  }

  var retreivedEventIs : Event = _
  var retreivedEventShould : Event = _

  it should "allow to retreive a specific event by id" in {
    retreivedEventIs = block(dbIs.retrieveSpecific(retreivedListFirstIs.head.id)).get
    retreivedEventShould  = block(dbShould.retrieveSpecific(retreivedListFirstShould.head.id)).get

    retreivedEventIs.equalExceptID(eventToInsert) shouldEqual true
    retreivedEventShould.equalExceptID(eventToInsert) shouldEqual true
  }

  it should "not allow inserts if something would intersect" in {
    val res = Await.result(dbIs.couldInsert(blockingEventToInsert), Duration(10, "second"))
    res shouldEqual false
    Await.result(dbShould.couldInsert(blockingEventToInsert), Duration(10, "second")) shouldEqual res
  }

  var updateIs : Event = _
  var updateShould: Event = _


  it should "allow a general update to an existing item" in {
    updateIs = retreivedEventIs.copy(email = newemail)
    updateShould = retreivedEventShould.copy(email = newemail)

    block(dbIs.update(updateIs)) shouldEqual Some(updateIs)
    block(dbShould.update(updateShould)) shouldEqual Some(updateShould)
    println(retreivedListFirstIs)
    println(retreivedListFirstShould)

    val curDB = block(dbIs.retrieve())

    curDB.head shouldEqual updateIs
    curDB.length shouldEqual 1
  }


  var confirmedEventIs : Event = _
  var confirmedEventShould : Event = _

    it should "allow a confirmation update to an existing item" in {
    confirmedEventIs = updateIs.copy(confirmedBySupseruser = true)
    confirmedEventShould = updateShould.copy(confirmedBySupseruser = true)


    block(dbIs.updateConfirmation(confirmedEventIs.id, confirmstatus = true)).get shouldEqual confirmedEventIs
    block(dbShould.updateConfirmation(confirmedEventShould.id, confirmstatus = true)).get shouldEqual confirmedEventShould
  }

  it should "allow deletion of such an item" in {

    block(dbIs.delete(confirmedEventIs.id)).get shouldEqual confirmedEventIs
    block(dbShould.delete(confirmedEventShould.id)).get shouldEqual confirmedEventShould
  }

  it should "not return a deleted item" in {
    block(dbIs.retrieveSpecific(confirmedEventIs.id)) shouldEqual None
    block(dbIs.retrieve()).length shouldEqual 0
  }


  it should "retreive correctly with time filtering" in {
    val slick = new BasicSlickH2Database(BasicSlickH2Database.dbRandomInMemory)
    block(slick.retrieve()).isEmpty shouldBe true

    val mock = new MockDatabase
    block(mock.retrieve()).isEmpty shouldBe true

    val from : Long = 2500
    val to   : Long = 7500

    val stringa = "somethinga"
    val stringb = "somethingb"

    //bed a from 0 to 1000
    val event1before : Event = Event(-1, Seq(EventElementBlock(availableItems.head, 0, 1000)), stringa, stringa,
      stringa,
      stringa, confirmedBySupseruser = false)
    //bed a from 1000 to 3000
    val event2cutsbefore : Event = Event(-1, Seq(EventElementBlock(availableItems.head, 1000, 3000)), stringa, stringa,
      stringa,
      stringa, confirmedBySupseruser = false)
    //bed a from 3000 to 5000
    val event3inner : Event = Event(-1, Seq(EventElementBlock(availableItems.head, 3000, 5000)), stringa, stringa,
      stringa,
      stringa, confirmedBySupseruser = false)
    //bed a from 5000 to 8000
    val event4cutsafter : Event = Event(-1, Seq(EventElementBlock(availableItems.head, 5000, 8000)), stringa, stringa,
      stringa,
      stringa, confirmedBySupseruser = false)
    //bed a from 8000 to 9000
    val event5after : Event = Event(-1, Seq(EventElementBlock(availableItems.head, 8000, 9000)), stringa, stringa,
      stringa,
      stringa, confirmedBySupseruser = false)
    //bed b from 1000 to 9000
    val event6allButOtherBed : Event = Event(-1, Seq(EventElementBlock("Some other room", 1000, 9000)), stringb,
      stringb,
      stringb,
      stringb, confirmedBySupseruser = false)

    availableItems.head.equals(availableItems.last) shouldEqual false

    val events = Seq(event1before, event2cutsbefore, event3inner, event4cutsafter, event5after, event6allButOtherBed)
    //inserting all of them
    events.foreach(e => {
      println("Creating: "+ e)
      block(slick.create(e)).get.equalExceptID(e) shouldEqual true
      block(mock.create(e)).get.equalExceptID(e) shouldEqual true
    })

    //all
    block(slick.retrieve()).length shouldEqual 6
    block(mock.retrieve()).length shouldEqual 6

    //only from
    block(slick.retrieve(fromInclusiveDate = from)).length shouldEqual 5
    block(mock.retrieve(fromInclusiveDate = from)).length shouldEqual 5


    //only to
    block(slick.retrieve(toExclusiveDate = to)).length shouldEqual 5
    block(mock.retrieve(toExclusiveDate = to)).length shouldEqual 5


    //from and to
    block(slick.retrieve(fromInclusiveDate = from, toExclusiveDate = to)).length shouldEqual 4
    block(mock.retrieve(fromInclusiveDate = from, toExclusiveDate = to)).length shouldEqual 4


  }



}
