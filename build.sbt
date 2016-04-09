name := "PlayMapRouletteV2"

version := "1.0"

lazy val `playmaproulettev2` = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)

scalaVersion := "2.11.7"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  evolutions,
  specs2 % Test,
  "com.typesafe.play" %% "anorm" % "3.0.0-M1",
  "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
  "net.postgis" % "postgis-jdbc" % "2.2.0",
  "org.webjars" %% "webjars-play" % "2.4.0-2",
  "org.webjars" % "bootstrap" % "3.3.6",
  "org.webjars" % "react" % "0.14.7",
  "org.webjars" % "font-awesome" % "4.5.0",
  "org.webjars" % "ionicons" % "2.0.1",
  "org.webjars" % "respond" % "1.4.2",
  "org.webjars" % "html5shiv" % "3.7.3",
  "org.webjars" % "jquery" % "2.2.1",
  "org.webjars" % "leaflet" % "0.7.7",
  "org.webjars" % "toastr" % "2.1.1",
  "joda-time" % "joda-time" % "2.9.2",
  "org.webjars" % "datatables" % "1.10.11",
  "com.vividsolutions" % "jts" % "1.13"
)

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )  

resolvers ++= Seq(
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  "Atlassian Releases" at "https://maven.atlassian.com/public/",
  Resolver.sonatypeRepo("snapshots")
)

scalacOptions ++= Seq(
  // Show warning feature details in the console
  "-feature",
  // Enable routes file splitting
  "-language:reflectiveCalls"
)

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"
