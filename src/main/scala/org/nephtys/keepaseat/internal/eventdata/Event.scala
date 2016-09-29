package org.nephtys.keepaseat.internal.eventdata

/**
  *
  * used in database, should be able to model every currently needed type of reservation (therefore does not have to be
  * generic yet)
  *
  * Uses japanese date system - YYYY-MM-DD---hh-mm
  *
  * Created by nephtys on 9/28/16.
  */
case class Event(
                id : Long,
                elements : Seq[EventElementBlock], //all blocks SHOULD (but are not required to) be on the same from/to
                // values
                name : String,
                email : String,
                telephone : String,
                commentary : String,
                confirmedBySupseruser : Boolean
                ) {

  //TODO: check these in validator before creating events
  //require(elements.nonEmpty)
  //require(elements.forall(s => s!= null && s.nonEmpty && s.toLowerCase().trim().equals(s)))

}

/**
  * this is remapped when writing to the database into a second table with a foreign key to Event
  * @param element
  * @param from
  * @param to
  */
case class EventElementBlock(
                            element : String,
                            from : Long,
                            to : Long
                            )