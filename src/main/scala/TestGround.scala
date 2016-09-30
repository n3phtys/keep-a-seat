import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server._
import Directives._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge}
import org.nephtys.cmac.BasicAuthHelper.LoginData
import org.nephtys.cmac.MacSource
import org.nephtys.keepaseat.internal.{LinkJWTRoute, StaticRoute}
import org.nephtys.keepaseat.internal.configs.{PasswordConfig, ServerConfig}
import org.nephtys.keepaseat.internal.eventdata.EventElementBlock
import org.nephtys.keepaseat.internal.linkkeys.{ConfirmationOrDeletion, ReservationRequest, SimpleReservation}
import org.nephtys.keepaseat.BasicSlickH2Database

import scala.io.StdIn

/**
  * Created by nephtys on 9/28/16.
  */
object TestGround extends App {
  println("Hello World")
  println("Loading Database...")
  val db = new BasicSlickH2Database(BasicSlickH2Database.dbFromFile("target/testdatabase"))

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

  import scala.concurrent.ExecutionContext.Implicits.global

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => actorSystem.terminate()) // and shutdown when done
}
