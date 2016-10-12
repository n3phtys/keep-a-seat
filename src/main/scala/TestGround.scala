import java.io.{File, FileInputStream}

import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server._
import Directives._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge}
import org.nephtys.cmac.BasicAuthHelper.LoginData
import org.nephtys.cmac.MacSource
import org.nephtys.keepaseat.filter.XSSCleaner
import org.nephtys.keepaseat.internal.{GetRetreiveRoute, LinkJWTRoute, StaticRoute}
import org.nephtys.keepaseat.internal.configs.{PasswordConfig, ServerConfig}
import org.nephtys.keepaseat.internal.eventdata.{Event, EventElementBlock}
import org.nephtys.keepaseat.internal.linkkeys.{ConfirmationOrDeletion, ReservationRequest, SimpleReservation}
import org.nephtys.keepaseat.internal.testmocks.MockDatabase
import org.nephtys.keepaseat.{BasicSlickH2Database, Databaseable, KeepASeat}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.io.StdIn
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by nephtys on 9/28/16.
  */
object TestGround extends App {

  implicit val passwordConfig : PasswordConfig = new PasswordConfig {
    override def normalUser: LoginData = LoginData("user", "1234")

    override def realmForCredentials(): String = "test credential realm"

    override def superUser: LoginData = LoginData("admin", "1234")
  }
  implicit val passwordConfigGetter = () => passwordConfig


  implicit val serverConfig = new ServerConfig {

    override def httpsPassword: Option[String] = Some("test")

    override def port: Int = 8080

    //assume "web" as default value
    override def pathToStaticWebDirectory(rootdirpath : String): String = rootdirpath + "/web"

    override def filepathToDatabaseWithoutFileEnding: Option[String] = None
  }


  val eventsWithoutIDs : Seq[Event] = Seq(
    Event(-1, Seq(EventElementBlock("Bed A", 1000, 2000)), "tom", "tom@mouse.com", "telephone", "event 1", false),
    Event(-1, Seq(EventElementBlock("Bed B", 1000, 2000)), "jerry", "jerry@cat.com", "telephone", "event 2", false),
    Event(-1, Seq(EventElementBlock("Bed A", 3000, 4000), EventElementBlock("Bed B", 3000, 4000)), "tom",
      "tom@mouse.com", "telephone", "event 3", false),
    Event(-1, Seq(EventElementBlock("Bed B", 8000, 10000)), "jerry", "jerry@cat.com", "telephone", "event 4", false),
    Event(-1, Seq(EventElementBlock("Bed A", 14000, 20000)), "tom", "tom@mouse.com", "telephone", "event 5", false)
  )

  println("Hello World")
  println("Loading Database...")
  implicit val db = new MockDatabase//new BasicSlickH2Database(BasicSlickH2Database.dbFromFile("target/testdatabase"))

  Await.result(Future.sequence(eventsWithoutIDs.map(e => db.create(e))), Duration(1, "minute"))


  implicit val actorSystem = ActorSystem("system")

  implicit val actorMaterializer = ActorMaterializer()

  val route =
    get {
      pathSingleSlash {
        complete {
          "Hello world from root"
        }
      } ~ path("hello") {
        complete {
          "Hello world from /hello"
        }
      }

    }

  implicit val xss = new XSSCleaner()

  val routeContainer = new GetRetreiveRoute()


  val pkcs12file = new FileInputStream(new File("certs/keystore.p12"))



  val https : HttpsConnectionContext = KeepASeat.createHTTPSContext(pkcs12file)
  Http().setDefaultServerHttpContext(https)
  val bindingFuture = Http().bindAndHandle(routeContainer.extractRoute ~ route, "localhost", serverConfig.port,
    connectionContext =  https)
  println(s"Server online at https://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => actorSystem.terminate()) // and shutdown when done
}
