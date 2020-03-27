import java.io.{BufferedWriter, FileWriter}

import scala.io.Source

name := "MapRouletteAPI"

version := "4.0.0"

scalaVersion := "2.13.1"

packageName in Universal := "MapRouletteAPI"

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

compileScalastyle := scalastyle.in(Compile).toTask("").value
(scalastyleConfig in Compile) := baseDirectory.value / "conf/scalastyle-config.xml"

lazy val `MapRouletteV2` = (project in file(".")).enablePlugins(PlayScala, SbtWeb, SwaggerPlugin)

swaggerDomainNameSpaces := Seq(
  "org.maproulette.framework.model",
  "org.maproulette.models",
  "org.maproulette.exception",
  "org.maproulette.session",
  "org.maproulette.actions",
  "org.maproulette.data"
)

swaggerOutputTransformers := Seq(envOutputTransformer)

swaggerRoutesFile := "generated.routes"

pipelineStages := Seq(gzip)

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  jdbc,
  jdbc % Test,
  ehcache,
  ws,
  evolutions,
  specs2 % Test,
  filters,
  guice,
  "com.typesafe.play"       %% "play-json-joda"     % "2.8.1",
  "com.typesafe.play"       %% "play-json"          % "2.8.1",
  "org.scalatestplus.play"  %% "scalatestplus-play" % "5.0.0" % Test,
  "org.webjars"             % "swagger-ui"          % "3.25.0",
  "org.playframework.anorm" %% "anorm"              % "2.6.5",
  "org.postgresql"          % "postgresql"          % "42.2.10",
  "net.postgis"             % "postgis-jdbc"        % "2.3.0",
  "joda-time"               % "joda-time"           % "2.10.5",
  "com.vividsolutions"      % "jts"                 % "1.13",
  "org.wololo"              % "jts2geojson"         % "0.14.3",
  "org.apache.commons"      % "commons-lang3"       % "3.9",
  "commons-codec"           % "commons-codec"       % "1.14",
  "com.typesafe.play"       %% "play-mailer"        % "8.0.0",
  "com.typesafe.play"       %% "play-mailer-guice"  % "8.0.0",
  "com.typesafe.akka"       %% "akka-cluster-tools" % "2.6.1",
  "com.typesafe.akka"       %% "akka-cluster-typed" % "2.6.1",
  "net.debasishg"           %% "redisclient"        % "3.20"
)

resolvers ++= Seq(
  Resolver.bintrayRepo("scalaz", "releases"),
  "Atlassian Releases" at "https://maven.atlassian.com/public/",
  Resolver.sonatypeRepo("snapshots")
)

scalacOptions ++= Seq(
  // Show warning feature details in the console
  "-feature",
  // Enable routes file splitting
  "-language:reflectiveCalls"
)

javaOptions in Test ++= Option(System.getProperty("config.file")).map("-Dconfig.file=" + _).toSeq

javaOptions in Compile ++= Seq(
  "-Xmx2G",
  // Increase stack size for compilation
  "-Xss4M"
)

lazy val buildRoutesFile = taskKey[Unit]("Builds the API V2 Routes File")
buildRoutesFile := {
  // delete the generated routes file first
  val generatedFile = baseDirectory.value / "conf/generated.routes"
  if (generatedFile.exists()) {
    generatedFile.delete()
  }
  generatedFile.createNewFile()

  // The apiv2.routes file should always be last as it contains the catch all routes
  val routeFiles = Seq(
    "project.api",
    "virtualproject.api",
    "challenge.api",
    "virtualchallenge.api",
    "task.api",
    "comment.api",
    "user.api",
    "data.api",
    "v2.api"
  )
  println(s"Generating Routes File from ${routeFiles.mkString(",")}")
  val writer = new BufferedWriter(new FileWriter(generatedFile))
  routeFiles.foreach(file => {
    val currentFile = Source.fromFile(baseDirectory.value / "conf/route" / file).getLines()
    for (line <- currentFile) {
      writer.write(s"$line\n")
    }
  })
  writer.close()
}

(compile in Compile) := ((compile in Compile) dependsOn buildRoutesFile).value
