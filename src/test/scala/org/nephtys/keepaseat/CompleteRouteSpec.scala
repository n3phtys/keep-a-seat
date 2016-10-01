package org.nephtys.keepaseat

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server._
import Directives._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge, HttpChallenges}
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsMissing
import org.nephtys.cmac.BasicAuthHelper.LoginData
import org.nephtys.cmac.MacSource
import org.nephtys.keepaseat.internal.{LinkJWTRoute, StaticRoute}
import org.nephtys.keepaseat.internal.configs.{PasswordConfig, ServerConfig}
import org.nephtys.keepaseat.internal.eventdata.EventElementBlock
import org.nephtys.keepaseat.internal.linkkeys.{ConfirmationOrDeletion, ReservationRequest, SimpleConfirmationOrDeletion, SimpleReservation}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by nephtys on 9/28/16.
  */
class CompleteRouteSpec extends WordSpec with Matchers with ScalatestRouteTest {

  val username = "john"
  val superusername = "superjohn"
  val userpassword = "12345"
  val superuserpassword = "678910"


  def readFile(filepath: String) = {
    val source = scala.io.Source.fromFile(filepath, "utf-8")
    val lines = try source.mkString finally source.close()
    lines
  }

  val secretKey = org.nephtys.cmac.HmacHelper.keys.generateNewKey(256, "HmacSHA256")
  implicit val macSource: MacSource = new MacSource(() => secretKey)


  val indexHTMLString: String = readFile("""src/test/resources/web/index.html""")
  assert(indexHTMLString.startsWith("""<!doctype html>"""))

  val fileTxtString: String = readFile("""src/test/resources/web/file.txt""")
  assert(fileTxtString.equals("""this is a txt file"""))

  val deeperTxtString: String = readFile("""src/test/resources/web/subdirectory/deeper.txt""")
  assert(deeperTxtString.equals("""I am so deep right now"""))


  val indexHTMLRedirect: String = """The request, and all future requests should be repeated using <a href="index.html">this URI</a>."""


  implicit val notifier = new MockMailer
  implicit val database = new MockDatabase

  implicit val serverConfigSource: () => ServerConfig = () => new ServerConfig {

    //assume "web" as default value
    override def pathToStaticWebDirectory: String = "src/test/resources/web"

    override def https: Boolean = false

    override def port: Int = 1234

    override def filepathToDatabaseWithoutFileEnding: Option[String] = None //should not be used anyway
  }

  implicit val passwordConfigSource: () => PasswordConfig = () => new PasswordConfig {
    override def normalUser: LoginData = LoginData(username, userpassword)

    override def superUser: LoginData = LoginData(superusername, superuserpassword)

    override def realmForCredentials(): String = "security realm for unit tests"
  }

  val staticRoute = new StaticRoute().extractRoute

  "The Static Route" should {
    "return a txt-file in the web direcrory for /file.txt" in {
      Get("file.txt") ~> staticRoute ~> check {
        responseAs[String] shouldEqual fileTxtString
      }
    }
    "return a txt-file inside a subdirectory of /web" in {
      Get("/subdirectory/deeper.txt") ~> staticRoute ~> check {
        responseAs[String] shouldEqual deeperTxtString
      }
    }
    "return index.html if looking at path /index.html" in {
      Get("/index.html") ~> staticRoute ~> check {
        responseAs[String] shouldEqual indexHTMLString
      }
    }
    "return index.html for empty path" in {
      Get() ~> staticRoute ~> check {
        responseAs[String] shouldEqual indexHTMLRedirect
      }
    }
  }

  val helloRt2 = path("hello") {
    get {
      complete {
        "Hello world"
      }
    }
  }

  val jwtRouteContainer = new LinkJWTRoute()
  val jwtRoute = jwtRouteContainer.extractRoute

  "The JWT-Link Route" should {

    def examplereservationlink: String = jwtRouteContainer.computeLinkSubpathForEmailConfirmation(examplereservation
      .toURLencodedJWT())

    def examplereservation: SimpleReservation = SimpleReservation(
      elements = Seq(EventElementBlock("Bed A", 9999, 9999 + (1000 * 3600 * 24))),
      name = "chris",
      email = "chris@somwhere.org",
      telephone = "013264355523434",
      commentary = "Some comment to make",
      randomNumber = 1337.asInstanceOf[Long])

    def examplereservation2 = examplereservation.copy(elements = Seq(EventElementBlock("Bed A", 9999 + 1000000, 9999 + 1000000 +
      (1000 *
        3600 * 24))))

    def exampleconfirm(combine: (Long, String)): ConfirmationOrDeletion = SimpleConfirmationOrDeletion(combine._1,
      combine._2,
      confirmingThisReservation = true, 13)
    def exampledecline(combine: (Long, String)): ConfirmationOrDeletion = SimpleConfirmationOrDeletion(combine
      ._1, combine._2,
      confirmingThisReservation = false, 14)


    def authmissingreject = AuthenticationFailedRejection.apply(AuthenticationFailedRejection.CredentialsMissing,
      HttpChallenges.basic(passwordConfigSource.apply().realmForCredentials()))

    "require basic auth on confirm-email" in {
      Get(jwtRouteContainer.computeLinkSubpathForEmailConfirmation(ReservationRequest.makeUrlencodedJWT(examplereservation))) ~> jwtRoute ~> check {
        rejection shouldEqual authmissingreject
      }
    }


    "work with user auth on confirm-email" in {
      //This does compile, red markers are intelliJ bugs
      Get(jwtRouteContainer.computeLinkSubpathForEmailConfirmation(ReservationRequest.makeUrlencodedJWT
      (examplereservation))) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        responseAs[String] shouldEqual jwtRouteContainer.emailConfirmSuccessText
      }
    }

    "require superuser auth on confirm-reservation" in {
      if (database.getUnconfirmedEventID.isDefined) {
        database.create(examplereservation.toNewEventWithoutID)
      }
      Get(jwtRouteContainer.computeLinkSubpathForSuperuserConfirmation(ConfirmationOrDeletion.makeUrlencodedJWT
      (exampleconfirm(database.getUnconfirmedEventID.get)))) ~> jwtRoute ~> check {
        rejection shouldEqual authmissingreject
      }
    }

    "work with superuser auth on confirm-reservation" in {
      //This does compile, red markers are intelliJ bugs
      if (database.getUnconfirmedEventID.isDefined) {
        database.create(examplereservation.toNewEventWithoutID)
      }
      Get(jwtRouteContainer.computeLinkSubpathForSuperuserConfirmation(ConfirmationOrDeletion.makeUrlencodedJWT(exampleconfirm(database.getUnconfirmedEventID.get)))) ~> addCredentials(BasicHttpCredentials(superusername,
        superuserpassword)) ~> jwtRoute ~> check {
        responseAs[String] shouldEqual jwtRouteContainer.confirmReservationText
      }
    }


    "be rejected if the jwt is incomplete" in {
      //This does compile, red markers are intelliJ bugs
      val long = jwtRouteContainer.computeLinkSubpathForEmailConfirmation(ReservationRequest.makeUrlencodedJWT
      (examplereservation))
      val short = long.substring(0, long.length / 2)
      Get(short) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        rejection shouldEqual akka.http.scaladsl.server.MalformedQueryParamRejection("jwt", "jwt unparsable")
      }
    }


    //following: check mechanic of route


    //test mechanic 1 - everything is right, but the user deletes after superuser confirmation
    "work normally under typical workflow with superuser confirming and user deleting afterwards" in {
      database.clearDatabase()
      notifier.notifications.clear()
      //get first email confirm link
      val emailconfirmlink = examplereservationlink
      Await.result(database.retrieve(), Duration(1, "second")).size shouldEqual 0
      //call get on email confirm link (with auth) => should create event in db
      Get(emailconfirmlink) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        status.isSuccess() shouldEqual true
      }
      val databaseretreive1 = Await.result(database.retrieve(), Duration(1, "second"))
      databaseretreive1.size shouldEqual 1
      val eventAfterEmailConfirm = databaseretreive1.head
      eventAfterEmailConfirm.confirmedBySupseruser shouldEqual false
      //get user delete and superuser confirm link
      notifier.notifications.size shouldEqual 2
      val userdeletelink: String = notifier.notifications.find(a => !a.toSuperuserInsteadOfUser).get.links.head
      val superuserconfirmlink: String = notifier.notifications.find(a => a.toSuperuserInsteadOfUser).get.links.head
      //call get on superuser confirm link (with auth)  => should update event in db
      Get(superuserconfirmlink) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        status.isSuccess() shouldEqual true
      }
      val databaseretreive2 = Await.result(database.retrieve(), Duration(1, "second"))
      databaseretreive2.size shouldEqual 1
      notifier.notifications.size shouldEqual 4
      val eventAfterSuperuserConfirm = databaseretreive2.head
      eventAfterSuperuserConfirm.confirmedBySupseruser shouldEqual true
      //call get on user delete link (with auth)  => should delete event in db
      Get(userdeletelink) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        status.isSuccess() shouldEqual true
      }
      val databaseretreive3 = Await.result(database.retrieve(), Duration(1, "second"))
      databaseretreive3.size shouldEqual 0
      notifier.notifications.size shouldEqual 6
    }


    //test mechanic 2 - everything is right, but the user deletes before superuser confirmation (which fails)
    "work with user deleting and superuser trying to confirm afterwards" in {
      database.clearDatabase()
      notifier.notifications.clear()
      //get first email confirm link
      val emailconfirmlink = examplereservationlink
      Await.result(database.retrieve(), Duration(1, "second")).size shouldEqual 0
      //call get on email confirm link (with auth) => should create event in db
      Get(emailconfirmlink) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        status.isSuccess() shouldEqual true
      }
      val databaseretreive1 = Await.result(database.retrieve(), Duration(1, "second"))
      databaseretreive1.size shouldEqual 1
      val eventAfterEmailConfirm = databaseretreive1.head
      eventAfterEmailConfirm.confirmedBySupseruser shouldEqual false
      //get user delete and superuser confirm link
      notifier.notifications.size shouldEqual 2
      val userdeletelink: String = notifier.notifications.find(a => !a.toSuperuserInsteadOfUser).get.links.head
      val superuserconfirmlink: String = notifier.notifications.find(a => a.toSuperuserInsteadOfUser).get.links.head
      //call get on user delete link (with auth)  => should delete event in db
      Get(userdeletelink) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        status.isSuccess() shouldEqual true
      }
      val databaseretreive3 = Await.result(database.retrieve(), Duration(1, "second"))
      databaseretreive3.size shouldEqual 0
      notifier.notifications.size shouldEqual 4
      //call get on superuser confirm link (with auth) - should fail
      Get(superuserconfirmlink) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        handled shouldEqual false
      }
      val databaseretreive2 = Await.result(database.retrieve(), Duration(1, "second"))
      databaseretreive2.size shouldEqual 0
      notifier.notifications.size shouldEqual 4

    }

    //test mechanic 3 - everything is right, but the superuser declines
    "work normally with the superuser declining" in {
      database.clearDatabase()
      notifier.notifications.clear()
      //get first email confirm link
      val emailconfirmlink = examplereservationlink
      Await.result(database.retrieve(), Duration(1, "second")).size shouldEqual 0
      //call get on email confirm link (with auth) => should create event in db
      Get(emailconfirmlink) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        status.isSuccess() shouldEqual true
      }
      val databaseretreive1 = Await.result(database.retrieve(), Duration(1, "second"))
      databaseretreive1.size shouldEqual 1
      val eventAfterEmailConfirm = databaseretreive1.head
      eventAfterEmailConfirm.confirmedBySupseruser shouldEqual false
      //get user delete and superuser delete link
      notifier.notifications.size shouldEqual 2
      val userdeletelink: String = notifier.notifications.find(a => !a.toSuperuserInsteadOfUser).get.links.head
      val superuserdeletelink: String = notifier.notifications.find(a => a.toSuperuserInsteadOfUser).get.links.last
      //call get on superuser delete  link (with auth)  => should delete event in db
      Get(superuserdeletelink) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        status.isSuccess() shouldEqual true
      }
      val databaseretreive4 = Await.result(database.retrieve(), Duration(1, "second"))
      databaseretreive4.size shouldEqual 0
      //call get on user delete link (with auth) - should fail
      Get(userdeletelink) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        handled shouldEqual false
      }
      val databaseretreive2 = Await.result(database.retrieve(), Duration(1, "second"))
      databaseretreive2.size shouldEqual 0
      notifier.notifications.size shouldEqual 4

    }

  }


}
