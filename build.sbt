
lazy val commonSettings = Seq(
  organization := "org.akkapaint",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.8"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)
    .settings(commonSettings: _*)
    .settings(name := "AkkaPaint")
    .settings(libraryDependencies ++= Seq(
      jdbc,
      cache,
      ws,
      "org.iq80.leveldb"            % "leveldb"          % "0.7",
      "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
      "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0-RC1" % Test
    ))
    .aggregate(akkaPaintShard, akkaPaintHistory, akkaPaintPerf)
    .dependsOn(akkaPaintShard, akkaPaintHistory)

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
    "com.datastax.cassandra" % "cassandra-driver-core" % "3.1.2",
    "com.lightbend.akka" %% "akka-stream-alpakka-cassandra" % "0.3"
  ))

lazy val akkaPaintPerf = (project in file("akkapaint-perf"))
  .settings(name := "AkkaPaint-Perf")
  .settings(commonSettings: _*)
  .enablePlugins(GatlingPlugin)
  .settings(libraryDependencies ++= Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.2.2",
    "io.gatling"            % "gatling-test-framework"    % "2.2.2",
    "com.typesafe.akka" %% "akka-http-experimental" % "2.4.10",
    "com.propensive" %% "rapture" % "2.0.0-M7"
  ))

lazy val akkaDependencies = {
  val akkaV = "2.4.14"
  Seq(
    "com.typesafe.akka" %% "akka-persistence" % akkaV,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaV,
    "com.typesafe.akka" %% "akka-remote" % akkaV,
    "com.typesafe.akka" %% "akka-cluster" % akkaV,
    "com.typesafe.akka" %% "akka-persistence-query-experimental" % akkaV,
    "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.22"
  )
}

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

SbtScalariform.defaultScalariformSettings
excludeFilter in scalariformFormat := (excludeFilter in scalariformFormat).value ||
  "Routes.scala" ||
  "ReverseRoutes.scala" ||
  "JavaScriptReverseRoutes.scala" ||
  "RoutesPrefix.scala"

