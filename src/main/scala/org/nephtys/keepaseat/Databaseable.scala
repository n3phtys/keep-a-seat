package org.nephtys.keepaseat

import org.nephtys.keepaseat.internal.eventdata.Event

/**
  * Created by nephtys on 9/28/16.
  */
trait Databaseable {

  def retrieve(from : Long = 0, to : Long = Long.MaxValue) : Seq[Event] = ???

  def update() = ???
  def create() = ???
  def delete() = ???
}
