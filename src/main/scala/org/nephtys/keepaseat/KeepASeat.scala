package org.nephtys.keepaseat

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.crypto.SecretKey
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import akka.http.scaladsl.server.Route
import org.nephtys.cmac.MacSource
import org.nephtys.keepaseat.internal.{GetRetreiveRoute, LinkJWTRoute, PostChangesRoute, StaticRoute}
import org.nephtys.keepaseat.internal.configs.{AnalogInterfaceConfig, CryptoConfig, PasswordConfig, ServerConfig}
import org.nephtys.keepaseat.internal.validators.{SuperuserPostValidator, UserPostValidator}
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.stream.ActorMaterializer
import org.nephtys.keepaseat.filter.XSSCleaner

/**
  * Created by nephtys on 9/28/16.
  */
object KeepASeat {

  /**
    * supply the required implicit parameters and gain routes used by keep-a-seat
    *
    * @param serverConfigSource
    * @param macSource
    * @param userPostValidators
    * @param superuserPostValidators
    * @param passwordConfigSource
    * @param database
    * @param emailNotifier
    * @return
    */
  def routeDefinitions(rootpathdir : String = ".")(implicit serverConfigSource: ServerConfig,
                         macSource: MacSource,
                         userPostValidators: Seq[UserPostValidator],
                         superuserPostValidators: Seq[SuperuserPostValidator],
                         passwordConfigSource: PasswordConfig,
                         database: Databaseable,
                         emailNotifier: MailNotifiable,
                         xssCleaner: XSSCleaner
  ): Route = {
    val retreiveRouter = new GetRetreiveRoute()
    val linkRouter = new LinkJWTRoute()
    val postRouter = new PostChangesRoute()
    val staticRouter = new StaticRoute(rootpathdir)

    retreiveRouter.extractRoute ~ linkRouter.extractRoute ~ postRouter.extractRoute ~ staticRouter.extractRoute
  }


  /**
    * taken from http://doc.akka.io/docs/akka/current/scala/http/server-side-https-support.html
    *
    * @param password
    * @param pkcs12file
    * @param system
    * @param mat
    * @return
    */
  def createHTTPSContextWithPassword(password: Array[Char], pkcs12file: InputStream, keystoreInstance: String =
  "PKCS12",
                         keymanagerInstance: String = "SunX509", trustmanagerInstance: String = "SunX509")
                        (implicit system: ActorSystem, mat: ActorMaterializer):
  HttpsConnectionContext = {
    implicit val dispatcher = system.dispatcher

    val ks: KeyStore = KeyStore.getInstance(keystoreInstance)

    require(pkcs12file != null, "Keystore required!")
    require(password != null, "Password required!")

    ks.load(pkcs12file, password)

    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance(keymanagerInstance)
    keyManagerFactory.init(ks, password)

    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(trustmanagerInstance)
    tmf.init(ks)

    val sslContext: SSLContext = SSLContext.getInstance("TLS")

    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)

    ConnectionContext.https(sslContext)
  }


  def createHTTPSContext(pkcs12file: InputStream, keystoreInstance: String = "PKCS12",
                         keymanagerInstance: String = "SunX509", trustmanagerInstance: String = "SunX509")
                        (implicit system: ActorSystem, mat: ActorMaterializer, serverConfig: ServerConfig):
  HttpsConnectionContext = {
    require(serverConfig.httpsPassword.isDefined,
      """createHTTPSContext, but https password was none. If the password " +
      "is empty, use Some("") instead.""")
    createHTTPSContextWithPassword(serverConfig.httpsPassword.get.toCharArray, pkcs12file,
      keystoreInstance, keymanagerInstance,
      trustmanagerInstance)
  }
}
