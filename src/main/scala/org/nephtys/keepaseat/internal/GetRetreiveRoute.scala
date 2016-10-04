package org.nephtys.keepaseat.internal

import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import org.nephtys.keepaseat.Databaseable
import org.nephtys.keepaseat.internal.configs.{Authenticators, PasswordConfig}
import spray.json._
import DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.nephtys.keepaseat.filter.XSSCleaner
import org.nephtys.keepaseat.internal.eventdata.{Event, EventElementBlock, EventSprayJsonFormat}




/**
  * Created by nephtys on 9/28/16.
  */
class GetRetreiveRoute(implicit passwordConfig : () => PasswordConfig, database : Databaseable,
                       xssCleaner : XSSCleaner) extends
  Directives with
  EventSprayJsonFormat  {

  def receivePathWithoutSlashes = """events"""
  def receivePath = "/"+receivePathWithoutSlashes

  def extractRoute : Route = path(receivePathWithoutSlashes) {
    authenticateBasic(passwordConfig.apply().realmForCredentials(), Authenticators.normalUserOrSuperuserAuthenticator
    (passwordConfig)) { username =>
      parameters('from.as[Long], 'to.as[Long]) { (from, to) => {
          onSuccess(database.retrieve(from, to)) {a => {
            complete{
              //TODO: json response header
              a.map(_.cleanHTML) //TODO: parse manually with upickle to guarantee output consistency
            }
          }
          }
      }
      }
    }
  }


}