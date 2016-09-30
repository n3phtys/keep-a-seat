package org.nephtys.keepaseat

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.model.StatusCodes
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

/**
  * Created by nephtys on 9/28/16.
  */
class CompleteRouteSpec extends WordSpec with Matchers with ScalatestRouteTest{

  val username = "john"
  val superusername = "superjohn"
  val userpassword = "12345"
  val superuserpassword ="678910"


  def readFile(filepath : String)  = {
    val source = scala.io.Source.fromFile(filepath, "utf-8")
    val lines = try source.mkString finally source.close()
    lines
  }

  val secretKey = org.nephtys.cmac.HmacHelper.keys.generateNewKey(256, "HmacSHA256")
  implicit val macSource : MacSource = new MacSource( () => secretKey)


  val indexHTMLString : String =  readFile("""src/test/resources/web/index.html""")
  assert(indexHTMLString.startsWith("""<!doctype html>"""))

  val fileTxtString : String =    readFile("""src/test/resources/web/file.txt""")
  assert(fileTxtString.equals("""this is a txt file"""))

  val deeperTxtString : String =  readFile("""src/test/resources/web/subdirectory/deeper.txt""")
  assert(deeperTxtString.equals("""I am so deep right now"""))


  val indexHTMLRedirect : String = """The request, and all future requests should be repeated using <a href="index.html">this URI</a>."""


  implicit val mocknotifier = new MockMailer
  implicit val mockdatabase = new MockDatabase

  implicit val serverConfigSource : () => ServerConfig = () => new ServerConfig {

    //assume "web" as default value
    override def pathToStaticWebDirectory: String = "src/test/resources/web"

    override def https: Boolean = false

    override def port: Int = 1234

    override def filepathToDatabaseWithoutFileEnding: Option[String] = None//should not be used anyway
  }

  implicit val passwordConfigSource : () => PasswordConfig = () => new PasswordConfig {
    override def normalUser: LoginData = LoginData(username, userpassword)

    override def superUser: LoginData = LoginData(superusername, superuserpassword)

    override def realmForCredentials(): String = "security realm for unit tests"
  }

  val staticRoute = new StaticRoute().extractRoute

  "The Static Route" should  {
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

  "The JWT-Link Route" should  {


    def examplereservation : SimpleReservation = SimpleReservation(
      elements = Seq(EventElementBlock("Bed A", 9999, 9999 +  (1000 * 3600 * 24))),
      name = "chris",
      email = "chris@somwhere.org",
      telephone = "013264355523434",
      commentary =  "Some comment to make",
        randomNumber = 1337.asInstanceOf[Long])

    def examplereservation2 = examplereservation.copy(elements = Seq(EventElementBlock("Bed A", 9999 + 1000000, 9999 + 1000000 +
      (1000 *
      3600 * 24))))

    def exampleconfirm(id : Long) : ConfirmationOrDeletion = SimpleConfirmationOrDeletion(id, confirmingThisReservation = true, 13)
      def exampledecline(id : Long) : ConfirmationOrDeletion = SimpleConfirmationOrDeletion(id, confirmingThisReservation = false, 14)


    def authmissingreject = AuthenticationFailedRejection.apply(AuthenticationFailedRejection.CredentialsMissing,
      HttpChallenges.basic(passwordConfigSource.apply().realmForCredentials()))

    "require basic auth on confirm-email" in {
      Get(jwtRouteContainer.computeLinkSubpathForEmailConfirmation(ReservationRequest.makeUrlencodedJWT(examplereservation))) ~> jwtRoute ~> check {
        rejection shouldEqual authmissingreject
      }
    }


    "work with user auth on confirm-email" in { //This does compile, red markers are intelliJ bugs
      Get(jwtRouteContainer.computeLinkSubpathForEmailConfirmation(ReservationRequest.makeUrlencodedJWT
      (examplereservation))) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        responseAs[String] shouldEqual jwtRouteContainer.emailConfirmSuccessText
      }
    }

    "require superuser auth on confirm-reservation" in {
      if(mockdatabase.getUnconfirmedEventID.isDefined) {
        mockdatabase.create(examplereservation.toNewEventWithoutID)
      }
      Get(jwtRouteContainer.computeLinkSubpathForSuperuserConfirmation(ConfirmationOrDeletion.makeUrlencodedJWT
      (exampleconfirm(mockdatabase.getUnconfirmedEventID.get)))) ~> jwtRoute ~> check {
        rejection shouldEqual authmissingreject
      }
    }

    "work with superuser auth on confirm-reservation" in { //This does compile, red markers are intelliJ bugs
      if(mockdatabase.getUnconfirmedEventID.isDefined) {
        mockdatabase.create(examplereservation.toNewEventWithoutID)
      }
      Get(jwtRouteContainer.computeLinkSubpathForSuperuserConfirmation(ConfirmationOrDeletion.makeUrlencodedJWT
      (exampleconfirm(mockdatabase.getUnconfirmedEventID.get)))) ~> addCredentials(BasicHttpCredentials(superusername,
        superuserpassword)) ~> jwtRoute ~> check {
        responseAs[String] shouldEqual jwtRouteContainer.confirmReservationText
      }
    }


    "is rejected if the jwt is incomplete" in { //This does compile, red markers are intelliJ bugs
      val long = jwtRouteContainer.computeLinkSubpathForEmailConfirmation(ReservationRequest.makeUrlencodedJWT
      (examplereservation))
      val short = long.substring(0, long.length / 2)
      Get(short) ~> addCredentials(BasicHttpCredentials(username, userpassword)) ~> jwtRoute ~> check {
        rejection shouldEqual akka.http.scaladsl.server.MalformedQueryParamRejection("jwt", "jwt unparsable")
      }
    }

    //TODO: check mechanic of route

  }


  /* copied from testkit example http://doc.akka.io/docs/akka/2.4.10/scala/http/routing-dsl/testkit.html  :

  val smallRoute =
    get {
      pathSingleSlash {
        complete {
          "Captain on the bridge!"
        }
      } ~
      path("ping") {
        complete("PONG!")
      }
    }

  "The service" should {

    "return a greeting for GET requests to the root path" in {
      // tests:
      Get() ~> smallRoute ~> check {
        responseAs[String] shouldEqual "Captain on the bridge!"
      }
    }

    "return a 'PONG!' response for GET requests to /ping" in {
      // tests:
      Get("/ping") ~> smallRoute ~> check {
        responseAs[String] shouldEqual "PONG!"
      }
    }

    "leave GET requests to other paths unhandled" in {
      // tests:
      Get("/kermit") ~> smallRoute ~> check {
        handled shouldBe false
      }
    }

    "return a MethodNotAllowed error for PUT requests to the root path" in {
      // tests:
      Put() ~> Route.seal(smallRoute) ~> check {
        status === StatusCodes.MethodNotAllowed
        responseAs[String] shouldEqual "HTTP method not allowed, supported methods: GET"
      }
    }
  }
}
   */
}
