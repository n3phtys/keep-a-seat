import org.nephtys.keepaseat.BasicSlickH2Database

/**
  * Created by nephtys on 9/28/16.
  */
object TestGround extends App {
  println("Hello World")
  println("Loading Database...")
  val db = new BasicSlickH2Database(BasicSlickH2Database.dbFromFile("target/testdatabase"))
}
