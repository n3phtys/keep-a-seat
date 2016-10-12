package org.nephtys.keepaseat

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.model.{HttpHeader, StatusCode, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server._
import Directives._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge, HttpChallenges}
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsMissing
import org.nephtys.cmac.BasicAuthHelper.LoginData
import org.nephtys.cmac.MacSource
import org.nephtys.keepaseat.internal.{GetRetreiveRoute, LinkJWTRoute, PostChangesRoute, StaticRoute}
import org.nephtys.keepaseat.internal.configs.{PasswordConfig, ServerConfig}
import org.nephtys.keepaseat.internal.eventdata.{Event, EventElementBlock, EventSprayJsonFormat}
import org.nephtys.keepaseat.internal.linkkeys.{ConfirmationOrDeletion, ReservationRequest, SimpleConfirmationOrDeletion, SimpleReservation}
import org.nephtys.keepaseat.internal.testmocks.{MockDatabase, MockMailer}
import spray.json._
import DefaultJsonProtocol._
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import org.nephtys.keepaseat.filter.XSSCleaner
import org.nephtys.keepaseat.internal.posts.{SimpleSuperuserPost, SimpleUserPost, UserPost}
import org.nephtys.keepaseat.internal.validators.{BasicSuperuserPostValidator, SuperuserPostValidator, UserPostValidator}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

/**
  * Created by nephtys on 9/28/16.
  */
class CompleteRouteSpec extends WordSpec with Matchers with ScalatestRouteTest with
  EventSprayJsonFormat {

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
  implicit val macSource: MacSource = new MacSource(secretKey)


  val indexHTMLString: String = readFile("""src/test/resources/web/index.html""")
  assert(indexHTMLString.startsWith("""<!doctype html>"""))

  val fileTxtString: String = readFile("""src/test/resources/web/file.txt""")
  assert(fileTxtString.equals("""this is a txt file"""))

  val deeperTxtString: String = readFile("""src/test/resources/web/subdirectory/deeper.txt""")
  assert(deeperTxtString.equals("""I am so deep right now"""))


  val indexHTMLRedirect: String = """The request, and all future requests should be repeated using <a href="index.html">this URI</a>."""


  implicit val notifier = new MockMailer
  implicit val database = new MockDatabase
  implicit val xss = new XSSCleaner()

  implicit val validatorsUser: Seq[UserPostValidator] = Seq.empty
  implicit val validatorsSuperuser: Seq[SuperuserPostValidator] = Seq(new BasicSuperuserPostValidator())

  implicit val serverConfigSource: ServerConfig =  new ServerConfig {

    //assume "web" as default value
    override def pathToStaticWebDirectory(rootdir : String): String = rootdir+"/web"


    override def port: Int = 1234

    override def filepathToDatabaseWithoutFileEnding: Option[String] = None //should not be used anyway
    override def httpsPassword: Option[String] = None
  }

  implicit val passwordConfigSource: PasswordConfig = new PasswordConfig {
    override def normalUser: LoginData = LoginData(username, userpassword)

    override def superUser: LoginData = LoginData(superusername, superuserpassword)

    override def realmForCredentials(): String = "security realm for unit tests"
  }

  val staticRoute = new StaticRoute("./src/test/resources").extractRoute

  "The Static Route" should {
    "require basic auth on accessing a file" in {
      Get("/file.txt") ~> staticRoute ~> check {
        rejection shouldEqual authmissingreject
      }
    }

    "require basic auth on root" in {
      Get() ~> staticRoute ~> check {
        rejection shouldEqual authmissingreject
      }
    }

    "return a txt-file in the web direcrory for /file.txt" in {
      println(serverConfigSource.pathToStaticWebDirectory("./src/test/resources"))
      Get("/file.txt") ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> staticRoute ~> check {
        responseAs[String] shouldEqual fileTxtString
      }
    }
    "return a txt-file inside a subdirectory of /web" in {
      Get("/subdirectory/deeper.txt") ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> staticRoute ~> check {
        responseAs[String] shouldEqual deeperTxtString
      }
    }
    "return index.html if looking at path /index.html" in {
      Get("/index.html") ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> staticRoute ~> check {
        responseAs[String] shouldEqual indexHTMLString
      }
    }
    "return index.html for empty path" in {
      Get() ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> staticRoute ~> check {
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


  def authmissingreject = AuthenticationFailedRejection.apply(AuthenticationFailedRejection.CredentialsMissing,
    HttpChallenges.basic(passwordConfigSource.realmForCredentials()))
  def superauthmissingreject = AuthenticationFailedRejection.apply(AuthenticationFailedRejection.CredentialsMissing,
    HttpChallenges.basic(passwordConfigSource.realmForCredentials()+"-adminrealm"))

  "The JWT-Link Route" should {

    def examplereservationlink: String = LinkJWTRoute.computeLinkSubpathForEmailConfirmation(examplereservation.toURLencodedJWT())

    def examplereservation: SimpleReservation = SimpleReservation(
      elements = Seq(EventElementBlock("Bed A", System.currentTimeMillis() + 9999, System.currentTimeMillis() +  9999 + (1000 * 3600 * 24))),
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



    "require basic auth on confirm-email" in {
      Get(LinkJWTRoute.computeLinkSubpathForEmailConfirmation(ReservationRequest.makeUrlencodedJWT(examplereservation))) ~> jwtRoute ~> check {
        rejection shouldEqual authmissingreject
      }
    }


    "work with user auth on confirm-email" in {
      //This does compile, red markers are intelliJ bugs
      Get(LinkJWTRoute.computeLinkSubpathForEmailConfirmation(ReservationRequest.makeUrlencodedJWT
      (examplereservation))) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        responseAs[String] shouldEqual LinkJWTRoute.emailConfirmSuccessText
      }
    }

    "require npormal user auth on confirm-reservation" in {
      if (database.getUnconfirmedEventID.isDefined) {
        database.create(examplereservation.toNewEventWithoutID)
      }
      Get(LinkJWTRoute.computeLinkSubpathForSuperuserConfirmation(ConfirmationOrDeletion.makeUrlencodedJWT
      (exampleconfirm(database.getUnconfirmedEventID.get)))) ~> jwtRoute ~> check {
        rejection shouldEqual authmissingreject
      }
    }

    "work with superuser auth on confirm-reservation" in {
      //This does compile, red markers are intelliJ bugs
      if (database.getUnconfirmedEventID.isDefined) {
        database.create(examplereservation.toNewEventWithoutID)
      }
      Get(LinkJWTRoute.computeLinkSubpathForSuperuserConfirmation(ConfirmationOrDeletion.makeUrlencodedJWT(exampleconfirm(database.getUnconfirmedEventID.get)))) ~> addCredentials(BasicHttpCredentials(superusername,
        superuserpassword)) ~> jwtRoute ~> check {
        responseAs[String] shouldEqual LinkJWTRoute.confirmReservationText
      }
    }


    "be rejected if the jwt is incomplete" in {
      //This does compile, red markers are intelliJ bugs
      val long = LinkJWTRoute.computeLinkSubpathForEmailConfirmation(ReservationRequest.makeUrlencodedJWT
      (examplereservation))
      val short = long.substring(0, long.length / 2)
      Get(short) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        rejection.asInstanceOf[akka.http.scaladsl.server.MalformedQueryParamRejection].errorMsg.startsWith("jwt for " +
          "email confirm " +
          "unparsable") shouldBe true
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
      Get(superuserconfirmlink) ~> addCredentials(BasicHttpCredentials(superusername, superuserpassword)) ~> jwtRoute ~>
        check {
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
      Get(superuserconfirmlink) ~> addCredentials(BasicHttpCredentials(superusername, superuserpassword)) ~> jwtRoute ~> check {
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
      Get(superuserdeletelink) ~> addCredentials(BasicHttpCredentials(superusername, superuserpassword)) ~> jwtRoute ~> check {
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

  val retreiveRouteContainer = new GetRetreiveRoute()
  val retreiveRoute = retreiveRouteContainer.extractRoute

  val eventsWithoutIDs: Seq[Event] = Seq(
    Event(-1, Seq(EventElementBlock("Bed A", 1000, 2000)), "tom", "tom@mouse.com", "telephone", "event 1", false),
    Event(-1, Seq(EventElementBlock("Bed B", 1000, 2000)), "jerry", "jerry@cat.com", "telephone", "event 2", false),
    Event(-1, Seq(EventElementBlock("Bed A", 3000, 4000), EventElementBlock("Bed B", 3000, 4000)), "tom",
      "tom@mouse.com", "telephone", "event 3", false),
    Event(-1, Seq(EventElementBlock("Bed B", 8000, 10000)), "jerry", "jerry@cat.com", "telephone", "event 4", false),
    Event(-1, Seq(EventElementBlock("Bed A", 14000, 20000)), "tom", "tom@mouse.com", "telephone", "event 5", false)
  )


  def fillDatabase()(implicit db: MockDatabase): Seq[Event] = {
    db.clearDatabase()
    Await.result(Future.sequence(eventsWithoutIDs.map(e => db.create(e))), Duration(1, "minute"))
    db.getAll
  }

  "The Retreive Route" should {


    val retreiveLink: String = retreiveRouteContainer.receivePath

    "retreive all events with min/max parameters" in {
      val dbvals = fillDatabase()
      val min: Long = Long.MinValue
      val max: Long = Long.MaxValue
      val mustVals = dbvals
      Get(retreiveLink + "?from=" + min + "&to=" + max) ~>
        addCredentials(BasicHttpCredentials(username, userpassword)) ~>
        retreiveRoute ~>
        check {
          responseAs[String].parseJson.convertTo[Seq[Event]] shouldEqual mustVals
        }
    }

    "retreive only a given period with correct from to parameters" in {
      val dbvals = fillDatabase()
      val min: Long = 2500
      val max: Long = 9500
      val mustVals: Seq[Event] = dbvals.filter(e => Databaseable.intersect(min, e.elements.map(_.from).min, max, e
        .elements.map(_.to).max))
      assert(mustVals.size == 2)
      Get(retreiveLink + "?from=" + min + "&to=" + max) ~>
        addCredentials(BasicHttpCredentials(username, userpassword)) ~>
        retreiveRoute ~>
        check {
          responseAs[String].parseJson.convertTo[Seq[Event]] shouldEqual mustVals
        }
    }


  }

  def xforwardhost: HttpHeader = HttpHeader.parse(PostChangesRoute.XForwardedHostHeader, "localhost:8000") match {
    case Ok(header, errors) => header
    case _ => throw new IllegalArgumentException()
  }

  def origin: HttpHeader = HttpHeader.parse(PostChangesRoute.OriginHeader, "localhost:8000") match {
    case Ok(header, errors) => header
    case _ => throw new IllegalArgumentException()
  }

  def xrequestedwith: HttpHeader = HttpHeader.parse(PostChangesRoute.XRequestedWithHeader, PostChangesRoute.XRequestedWithValue) match {
    case Ok(header, errors) => header
    case _ => throw new IllegalArgumentException()
  }

  def mailer = notifier

  "The POST Routes" should {
    import upickle.default._

    val routecontainer = new PostChangesRoute()
    val route = routecontainer.extractRoute

    def correctUserpostJson: String = write(SimpleUserPost("john", "john@somewhere.org", "32525 555", "this is a " +
      "comment",
      Seq(EventElementBlock("Bed A", 24345, 50000))))
    //Test Case 1 - correct userpostroute should lead to complete and confirm mail
    "evolve a correct userpost to a a mail with confirm link and a complete" in {
      val oldmailersize = mailer.notifications.size
      Post(PostChangesRoute.userPostPath, correctUserpostJson) ~> addHeaders(xforwardhost, origin, xrequestedwith) ~>
        addCredentials(BasicHttpCredentials(username, userpassword)) ~> route ~> check {
        responseAs[String] shouldEqual PostChangesRoute.userresponsetext
      }
      mailer.notifications.size shouldEqual (oldmailersize + 1)
      mailer.notifications.last.sumOfFlags shouldEqual (1) //confirm email link notification
    }

    //Test Case 2 - nonsensical json should lead to reject
    def nonsensicaluserpostjson: String = "{'hackidiy hack' : true}"
    "reject nonsensical userposts" in {
      Post(PostChangesRoute.userPostPath, nonsensicaluserpostjson) ~>
        addCredentials(BasicHttpCredentials(username, userpassword)) ~> addHeaders(xforwardhost, origin, xrequestedwith) ~> route ~> check {
        handled shouldEqual false
      }
    }

    //Test Case 3 - superuserroute with delete should lead to complete and delete from db and mails
    "complete a superuserpost based delete" in {
      val dbvals = fillDatabase()
      def idToDeleteBySuperuser = dbvals.head.id
      def superuserpostdeletejson: String = write(SimpleSuperuserPost(idToDeleteBySuperuser, Some(true), None))
      val oldmailersize = mailer.notifications.size
      Await.result(database.retrieveSpecific(idToDeleteBySuperuser), Duration(1, "second")).isDefined shouldEqual (true)

      Post(PostChangesRoute.superuserPostPath, superuserpostdeletejson) ~>
        addCredentials(BasicHttpCredentials(superusername, superuserpassword)) ~> addHeaders(xforwardhost, origin, xrequestedwith) ~> route ~> check {
        responseAs[String] shouldEqual s"Event with ID = $idToDeleteBySuperuser was deleted"
      }
      Await.result(database.retrieveSpecific(idToDeleteBySuperuser), Duration(1, "second")) shouldEqual (None)
      mailer.notifications.size shouldEqual (oldmailersize + 1)
      mailer.notifications.last.sumOfFlags shouldEqual (4) //decline to user notification
    }

    //Test Case 4 - superuserroute with confirm should lead to complete and db changes and mails
    "complete a superuserpost based confirm" in {
      val dbvals = fillDatabase()
      def idToConfirmBySuperuser = dbvals.head.id
      def superuserpostconfirmjson: String = write(SimpleSuperuserPost(idToConfirmBySuperuser, None, Some(true)))
      val oldmailersize = mailer.notifications.size
      Await.result(database.retrieveSpecific(idToConfirmBySuperuser), Duration(1, "second")).get.confirmedBySupseruser
        .shouldEqual(false)

      Post(PostChangesRoute.superuserPostPath, superuserpostconfirmjson) ~>
        addCredentials(BasicHttpCredentials(superusername, superuserpassword)) ~> addHeaders(xforwardhost, origin, xrequestedwith) ~> route ~> check {
        responseAs[String] shouldEqual s"Event with ID = ${idToConfirmBySuperuser} was confirmed"
      }
      Await.result(database.retrieveSpecific(idToConfirmBySuperuser), Duration(1, "second")).get.confirmedBySupseruser
        .shouldEqual(true)
      mailer.notifications.size shouldEqual (oldmailersize + 2)
      mailer.notifications.apply(mailer.notifications.size - 2).sumOfFlags.shouldEqual(10)
      mailer.notifications.last.sumOfFlags shouldEqual (2)
    }

    //Test Case 5 - superuserroute with unconfirm should lead to complete and db changes and mails
    "complete a superuserpost based unconfirm" in {
      val dbvals = fillDatabase()
      def idToUnConfirmBySuperuser = dbvals.head.id
      Await.result(database.updateConfirmation(idToUnConfirmBySuperuser, true), Duration(1, "second"))
      def superuserpostunconfirmjson: String = write(SimpleSuperuserPost(idToUnConfirmBySuperuser, None, Some(false)))
      val oldmailersize = mailer.notifications.size
      Await.result(database.retrieveSpecific(idToUnConfirmBySuperuser), Duration(1, "second")).get.confirmedBySupseruser
        .shouldEqual(true)

      Post(PostChangesRoute.superuserPostPath, superuserpostunconfirmjson) ~>
        addCredentials(BasicHttpCredentials(superusername, superuserpassword)) ~> addHeaders(xforwardhost, origin, xrequestedwith) ~> route ~> check {
        responseAs[String] shouldEqual s"Event with ID = ${idToUnConfirmBySuperuser} was set to unconfirmed"
      }
      Await.result(database.retrieveSpecific(idToUnConfirmBySuperuser), Duration(1, "second")).get.confirmedBySupseruser
        .shouldEqual(false)
      mailer.notifications.size shouldEqual (oldmailersize + 1)
      mailer.notifications.last.sumOfFlags shouldEqual (0)
    }
  }



  "The Complete Routeset" should {

    //Test one case with normal reservation from start to finish (calling get route before, during, and after it)
    "allow a normal reservation from start to finish" in {
      import upickle.default._
      //combined route with own database and mailer
      implicit val db = new MockDatabase()
      implicit val mailer = new MockMailer()
      //implicit val password = this.passwordConfigSource
      val route = KeepASeat.routeDefinitions()(userPostValidators = this.validatorsUser, superuserPostValidators =
        this.validatorsSuperuser, serverConfigSource = this.serverConfigSource,
        xssCleaner = this.xss, macSource = this.macSource,
        database = db, passwordConfigSource = this.passwordConfigSource,
        emailNotifier = mailer)

      //fill/clear database and mockdatabase
      val dbvals = fillDatabase()(db)
      val postBlocked: SimpleUserPost = SimpleUserPost("Eve", "Eve@somewhere.com", "0315235 2352432 23523", "just a normal" +
        " registration", dbvals.head.elements)
      val postFree: SimpleUserPost = SimpleUserPost("Eve", "Eve@somewhere.com", "0315235 2352432 23523", "just a normal" +
        " registration", Seq(EventElementBlock("Bed A", dbvals.map(_.elements.map(_.to).max).max + 1000, dbvals.map(_
        .elements.map(_.to).max).max + 5000)))
      val freeid: Long = dbvals.map(_.id).max + 1
      val eventwithidUnconfirmed: Event = Event(freeid, postFree.elements, postFree.name, postFree.email, postFree
        .telephone,
        postFree.commentary, confirmedBySupseruser = false)
      val firsgetShouldResult: Seq[Event] = dbvals
      val secondgetShouldResult: Seq[Event] = firsgetShouldResult.+:(eventwithidUnconfirmed)
      val thirdgetShouldResult: Seq[Event] = firsgetShouldResult.+:(
        eventwithidUnconfirmed.copy(confirmedBySupseruser = true))
      def emailconfirmlink: String = mailer.notifications.filter(e => e.sumOfFlags == 1).last.links.head
      def superuserconfirmlink: String = mailer.notifications.filter(e => e.sumOfFlags == 8).last.links.head
      def freepostresponsetext: String = "You have received an email containing a link. Press that link to confirm " +
        "your email address."
      def emailconfirmresponsetext: String = LinkJWTRoute.emailConfirmSuccessText
      def superuserconfirmresponsetext: String = LinkJWTRoute.confirmReservationText

      implicit val ordering: Ordering[Event] = new scala.math.Ordering[Event]() {
        override def compare(x: Event, y: Event): Int = x.id.compareTo(y.id)
      }

      //get and verify results
      val min: Long = Long.MinValue
      val max: Long = Long.MaxValue
      Get("/events" + "?from=" + min + "&to=" + max) ~>
        addCredentials(BasicHttpCredentials(username, userpassword)) ~> route ~> check {
        responseAs[String].parseJson.convertTo[Seq[Event]].sorted shouldEqual firsgetShouldResult.sorted
      }

      //userpost on blocked time (should be rejected)
      Post("/newevent", write(postBlocked)) ~>
        addCredentials(BasicHttpCredentials(username, userpassword)) ~> addHeaders(xforwardhost, origin, xrequestedwith) ~> route ~> check {
        handled shouldEqual false
      }

      //userpost on time not blocked (should be accepted)
      Post("/newevent", write(postFree)) ~>
        addCredentials(BasicHttpCredentials(username, userpassword)) ~> addHeaders(xforwardhost, origin, xrequestedwith) ~> route ~> check {
        responseAs[String] shouldEqual freepostresponsetext
      }

      //extract confirm link from mailer & confirm via link
      //println(dbvals)
      //println(emailconfirmlink)
      //println(postFree)
      Get(emailconfirmlink) ~>
        addCredentials(BasicHttpCredentials(username, userpassword)) ~> route ~> check {
        responseAs[String] shouldEqual emailconfirmresponsetext
      }

      //get and check that event is inserted
      Get("/events" + "?from=" + min + "&to=" + max) ~>
        addCredentials(BasicHttpCredentials(username, userpassword)) ~> route ~> check {
        responseAs[String].parseJson.convertTo[Seq[Event]].sorted shouldEqual secondgetShouldResult.sorted
      }
      //extract superuser confirm link from mailer & confirm via link
      Get(superuserconfirmlink) ~>
        addCredentials(BasicHttpCredentials(superusername, superuserpassword)) ~> route ~> check {
        responseAs[String] shouldEqual superuserconfirmresponsetext
      }
      //get and check that event is inserted and both superuser and user received mail
      Get("/events" + "?from=" + min + "&to=" + max) ~>
        addCredentials(BasicHttpCredentials(username, userpassword)) ~> route ~> check {
        responseAs[String].parseJson.convertTo[Seq[Event]].sorted shouldEqual thirdgetShouldResult.sorted
      }
      //println(mailer.notifications.map(_.sumOfFlags))
      mailer.notifications.apply(mailer.notifications.size - 2).sumOfFlags shouldEqual 10
      mailer.notifications.apply(mailer.notifications.size - 1).sumOfFlags shouldEqual 2
    }
  }
}
