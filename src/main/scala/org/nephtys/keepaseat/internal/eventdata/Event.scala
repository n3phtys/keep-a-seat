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
                from : String,
                to : String,
                element : Seq[String], //has to be trimmed and lowercase
                name : String,
                email : String,
                telephone : String,
                commentary : String
                ) {

  //TODO: check these in validator before creating events
  require(element.nonEmpty)
  require(element.forall(s => s!= null && s.nonEmpty && s.toLowerCase().trim().equals(s)))

}
