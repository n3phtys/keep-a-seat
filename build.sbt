name := "keep-a-seat"

version := "0.3.0"

scalaVersion := "2.11.8"

val scalacmcurl =  "https://github.com/n3phtys/scala-cmac.git"



val akkaV       = "2.4.11"
val scalaTestV  = "3.0.0"
val upickleV    = "0.4.1"
val slf4jV      = "1.6.4"
val slickV      = "3.1.1"
val h2dbV       = "1.4.192"
val sprayV      = "1.3.2"
val htmlsanitizerV   = "20160924.1"



lazy val cmacProject = RootProject(uri(scalacmcurl))


// Library dependencies
lazy val root = Project("keep-a-seat", file("."))
  .settings(
    publish := {},
    publishLocal := {}
  )
  .dependsOn(cmacProject)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"   %% "akka-actor" % akkaV,
      "com.typesafe.akka"   %% "akka-stream" % akkaV,
      "com.typesafe.akka"   %% "akka-http-experimental" % akkaV,
      "com.typesafe.akka"   %% "akka-http-spray-json-experimental" % akkaV,
      "com.typesafe.akka"   %% "akka-http-testkit" % akkaV,
      "org.scalatest"       %% "scalatest" % scalaTestV % "test",
      "com.typesafe.akka"   %% "akka-cluster-sharding"  % akkaV,
      "com.typesafe.akka"   %% "akka-cluster-tools" % akkaV,
      "com.lihaoyi"         %% "upickle" % upickleV,
      "com.typesafe.slick"  %% "slick" % slickV,
      //"io.spray"            %% "spray-json" % sprayV, not used currently
      "com.h2database"      %  "h2"  % h2dbV,
      "com.googlecode.owasp-java-html-sanitizer" % "owasp-java-html-sanitizer" % htmlsanitizerV,
      "com.google.guava" % "guava" % "19.0",
      "com.google.code.findbugs" % "jsr305" % "3.0.1", //could be compile time only if needed
      "org.slf4j" % "slf4j-nop" % slf4jV
    ))