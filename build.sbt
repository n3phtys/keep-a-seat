name := "keep-a-seat"

version := "0.0"

scalaVersion := "2.11.8"

val scalacmcurl =  "https://github.com/n3phtys/scala-cmac.git"
val scavalueurl = "https://github.com/n3phtys/scavalue-loader.git"



val akkaV       = "2.4.10"
val scalaTestV  = "3.0.0"
val upickleV    = "0.4.1"



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
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-stream" % akkaV,
      "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
      "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaV,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaV,
      "org.scalatest"     %% "scalatest" % scalaTestV % "test",
      "com.typesafe.akka" %% "akka-cluster-sharding"  % akkaV,
      "com.typesafe.akka" %% "akka-cluster-tools" % akkaV,
      "com.lihaoyi" %% "upickle" % upickleV
    ))