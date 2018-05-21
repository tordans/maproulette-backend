logLevel := Level.Warn

// The Typesafe repository
resolvers ++= Seq(
  Resolver.bintrayRepo("scalaz", "releases"),
  Resolver.bintrayIvyRepo("iheartradio", "sbt-plugins")
)

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.13")

addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.1.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-jshint" % "1.0.4")

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")

//addSbtPlugin("com.typesafe.sbt" % "sbt-rjs" % "1.0.7")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.8.0")

addSbtPlugin("com.iheart" % "sbt-play-swagger" % "0.6.2-PLAY2.6")
