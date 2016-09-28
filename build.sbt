name := "keep-a-seat"

version := "0.0"

scalaVersion := "2.11.8"

val scalacmcurl =  "https://github.com/n3phtys/scala-cmac.git"
val scavalueurl = "https://github.com/n3phtys/scavalue-loader.git"

lazy val cmacProject = RootProject(uri(scalacmcurl))
lazy val scavalueProject = RootProject(uri(scavalueurl))


// Library dependencies
lazy val root = Project("keep-a-seat", file("."))
  .settings(
    publish := {},
    publishLocal := {}
  )
  .dependsOn(cmacProject, scavalueProject)
  .settings(
    libraryDependencies ++= Seq(

    ))