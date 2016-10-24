package org.nephtys.keepaseat.internal

import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.nephtys.keepaseat.Databaseable
import org.nephtys.keepaseat.filter.XSSCleaner
import org.nephtys.keepaseat.internal.configs.{Authenticators, PasswordConfig}
import upickle.default._


/**
  * Created by nephtys on 9/28/16.
  */
class GetRetreiveRoute(implicit passwordConfig: PasswordConfig, database: Databaseable,
                       xssCleaner: XSSCleaner) {

  def receivePathWithoutSlashes = """events"""

  def receivePath = "/" + receivePathWithoutSlashes

  def extractRoute: Route = path(receivePathWithoutSlashes) {
    authenticateBasic(passwordConfig.realmForCredentials, Authenticators.normalUserOrSuperuserAuthenticator
    (passwordConfig)) { username =>
      parameters('from.as[Long], 'to.as[Long]) { (from, to) => {
        onSuccess(database.retrieve(from, to)) { a => {
          complete {
            HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`application/json`), write(a.sortBy(_.elements.map(_.from).min).map(_.cleanHTML))))
          }
        }
        }
      }
      }
    }
  }


}