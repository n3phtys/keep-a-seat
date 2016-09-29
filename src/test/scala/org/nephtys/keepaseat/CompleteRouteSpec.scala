package org.nephtys.keepaseat

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server._
import Directives._
import org.nephtys.keepaseat.internal.StaticRoute
import org.nephtys.keepaseat.internal.configs.ServerConfig

/**
  * Created by nephtys on 9/28/16.
  */
class CompleteRouteSpec extends WordSpec with Matchers with ScalatestRouteTest{

  def readFile(filepath : String)  = {
    val source = scala.io.Source.fromFile(filepath, "utf-8")
    val lines = try source.mkString finally source.close()
    lines
  }

  val indexHTMLString : String =  readFile("""src/test/resources/web/index.html""")
  assert(indexHTMLString.startsWith("""<!doctype html>"""))

  val fileTxtString : String =    readFile("""src/test/resources/web/file.txt""")
  assert(fileTxtString.equals("""this is a txt file"""))

  val deeperTxtString : String =  readFile("""src/test/resources/web/subdirectory/deeper.txt""")
  assert(deeperTxtString.equals("""I am so deep right now"""))


  val indexHTMLRedirect : String = """The request, and all future requests should be repeated using <a href="index.html">this URI</a>."""


  implicit val serverConfigSource : () => ServerConfig = () => new ServerConfig {
    override def httpsOnly: Option[Boolean] = None

    //assume "web" as default value
    override def pathToStaticWebDirectory: String = "src/test/resources/web"

    override def https: Boolean = false

    override def port: Int = 1234
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
