name := """AkkaPaint"""

organization := "org.akkapaint"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
  .aggregate(akkaPaintShard)
    .dependsOn(akkaPaintShard)

lazy val akkaPaintShard = project in file("akkapaint-shard")

lazy val akkaPaintPerf = (project in file("akkapaint-perf"))
  .enablePlugins(GatlingPlugin)
  .settings(libraryDependencies += "com.propensive" %% "rapture" % "2.0.0-M7")

scalaVersion := "2.11.8"

libraryDependencies ++= {
  val akkaV = "2.4.10"
  Seq(
    jdbc,
    cache,
    ws,
    "com.typesafe.akka" %% "akka-persistence" % akkaV,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaV,
    "com.typesafe.akka" %% "akka-remote" % akkaV,
    "com.typesafe.akka" %% "akka-cluster" % akkaV,
    "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.18",
    "org.iq80.leveldb"            % "leveldb"          % "0.7",
    "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
    "com.trueaccord.scalapb" %% "scalapb-runtime" % "0.4.20",
    "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0-RC1" % Test
  )
}

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

SbtScalariform.defaultScalariformSettings
excludeFilter in scalariformFormat := (excludeFilter in scalariformFormat).value ||
  "Routes.scala" ||
  "ReverseRoutes.scala" ||
  "JavaScriptReverseRoutes.scala" ||
  "RoutesPrefix.scala"

