name := """AkkaPaint-Perf"""

organization := "org.akkapaint"

scalaVersion := "2.11.8"

libraryDependencies ++= {
  Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.2.2",
    "io.gatling"            % "gatling-test-framework"    % "2.2.2",
    "com.typesafe.akka" %% "akka-http-experimental" % "2.4.10",
    "com.propensive" %% "rapture" % "2.0.0-M7"
  )
}

lazy val akkaPaintPerf = (project in file("."))
  .enablePlugins(GatlingPlugin)