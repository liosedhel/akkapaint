
lazy val commonSettings = Seq(
  organization := "org.akkapaint",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.12.8"
)

val akkaV = "2.5.17"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
    .settings(commonSettings: _*)
    .settings(name := "AkkaPaint")
    .settings(libraryDependencies ++= Seq(
      jdbc,
      cache,
      ws,
      guice,
      "org.iq80.leveldb"            % "leveldb"          % "0.7",
      "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
      "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
    ))
    .aggregate(akkaPaintShard, akkaPaintHistory, akkaPaintPerf)
    .dependsOn(akkaPaintShard, akkaPaintHistory, akkaPaintPerf)

lazy val akkaPaintShard = (project in file("akkapaint-shard"))
  .settings(commonSettings: _*)
  .settings(name := "AkkaPaint-Shard")
  .settings(libraryDependencies ++= akkaDependencies)
  .settings(PB.targets in Compile := Seq(
    scalapb.gen() -> (sourceManaged in Compile).value
  ))

lazy val akkaPaintHistory = (project in file("akkapaint-history"))
  .settings(name := "AkkaPaint-History")
  .settings(commonSettings: _*)
  .dependsOn(akkaPaintShard)
  .settings(libraryDependencies ++= Seq(
    "joda-time" % "joda-time" % "2.9.6",
    "com.datastax.cassandra" % "cassandra-driver-core" % "3.5.1",
    "com.lightbend.akka" %% "akka-stream-alpakka-cassandra" % "1.0-M1"
  ))

lazy val akkaPaintPerf = (project in file("akkapaint-perf"))
  .settings(name := "AkkaPaint-Perf")
  .settings(commonSettings: _*)
  .enablePlugins(GatlingPlugin)
  .settings(libraryDependencies ++= Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.3.1",
    "io.gatling"            % "gatling-test-framework"    % "2.3.1",
    "com.typesafe.akka" %% "akka-http" % "10.1.5",
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.propensive" %% "rapture" % "2.0.0-M9"
  ))

lazy val akkaDependencies = {
  Seq(
    "com.typesafe.akka" %% "akka-persistence" % akkaV,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaV,
    "com.typesafe.akka" %% "akka-remote" % akkaV,
    "com.typesafe.akka" %% "akka-cluster" % akkaV,
    "com.typesafe.akka" %% "akka-persistence-query" % akkaV,
    "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.54"
  )
}

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

//excludeFilter in scalariformFormat := (excludeFilter in scalariformFormat).value ||
//  "Routes.scala" ||
//  "ReverseRoutes.scala" ||
//  "JavaScriptReverseRoutes.scala" ||
//  "RoutesPrefix.scala"

