package org.nephtys.keepaseat.internal.eventdata

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{CompactPrinter, DefaultJsonProtocol}
import org.nephtys.keepaseat.internal.configs.{Authenticators, PasswordConfig}
import spray.json._
import DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

/**
  * Created by nephtys on 10/1/16.
  */
trait EventSprayJsonFormat extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val printer = CompactPrinter
  implicit val blockFormat = jsonFormat3(EventElementBlock)
  implicit val eventFormat = jsonFormat7(Event)
}