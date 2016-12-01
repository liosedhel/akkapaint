name := """AkkaPaint-Shard"""

organization := "org.akkapaint"

scalaVersion := "2.11.8"

libraryDependencies ++= {
  val akkaV = "2.4.14"
  Seq(
    "com.typesafe.akka" %% "akka-persistence" % akkaV,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaV,
    "com.typesafe.akka" %% "akka-remote" % akkaV,
    "com.typesafe.akka" %% "akka-cluster" % akkaV,
    "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.21",
    "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0-RC1" % Test
  )
}



PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

