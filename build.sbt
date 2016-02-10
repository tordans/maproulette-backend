name := "PlayMapRouletteV2"

version := "1.0"

lazy val `playmaproulettev2` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  evolutions,
  specs2 % Test,
  "com.typesafe.play" %% "anorm" % "2.4.0",
  "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
  "net.postgis" % "postgis-jdbc" % "2.2.0"
)

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )  

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"  
