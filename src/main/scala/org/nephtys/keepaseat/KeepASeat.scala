package org.nephtys.keepaseat

import javax.crypto.SecretKey

import akka.http.scaladsl.server.Route
import org.nephtys.cmac.MacSource
import org.nephtys.keepaseat.internal.{GetRetreiveRoute, LinkJWTRoute, PostChangesRoute, StaticRoute}
import org.nephtys.keepaseat.internal.configs.{AnalogInterfaceConfig, CryptoConfig, PasswordConfig, ServerConfig}
import org.nephtys.keepaseat.internal.validators.{SuperuserPostValidator, UserPostValidator}
import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import org.nephtys.keepaseat.filter.XSSCleaner

/**
  * Created by nephtys on 9/28/16.
  */
object KeepASeat {

  def cryptoConfig : CryptoConfig = ???

  val secretKeySource = new org.nephtys.genericvalueloader.GenericValueLoader[SecretKey](null, Some(() => {1000 * 60 *
    60 *
    24}.toLong),
    null, null, null, null)

  val macSource = new MacSource(() => secretKeySource.getValue())

  /**
    * supply the required implicit parameters and gain routes used by keep-a-seat
    * @param serverConfigSource
    * @param macSource
    * @param userPostValidators
    * @param superuserPostValidators
    * @param passwordConfigSource
    * @param analogInterfaceConfigSource
    * @param database
    * @param emailNotifier
    * @return
    */
  def routeDefinitions()(implicit serverConfigSource : () => ServerConfig,
                         macSource : MacSource,
                         userPostValidators : Seq[UserPostValidator],
                         superuserPostValidators : Seq[SuperuserPostValidator],
                         passwordConfigSource : () => PasswordConfig,
                         analogInterfaceConfigSource : () => AnalogInterfaceConfig,
                         database : Databaseable,
                         emailNotifier : MailNotifiable,
                         xssCleaner : XSSCleaner
  ) : Route = {
    val retreiveRouter = new GetRetreiveRoute()
    val linkRouter = new LinkJWTRoute()
    val postRouter = new PostChangesRoute()
    val staticRouter = new StaticRoute()

    retreiveRouter.extractRoute ~ linkRouter.extractRoute ~ postRouter.extractRoute ~ staticRouter.extractRoute
  }

}
