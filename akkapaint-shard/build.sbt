name := """AkkaPaint-Shard"""

organization := "org.akkapaint"

scalaVersion := "2.11.8"

libraryDependencies ++= {
  val akkaV = "2.4.12"
  Seq(
    "com.typesafe.akka" %% "akka-persistence" % akkaV,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaV,
    "com.typesafe.akka" %% "akka-remote" % akkaV,
    "com.typesafe.akka" %% "akka-cluster" % akkaV,
    "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.20",
    "com.trueaccord.scalapb" %% "scalapb-runtime" % "0.4.20",
    "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0-RC1" % Test
  )
}


import com.trueaccord.scalapb.{ScalaPbPlugin => PB}

PB.flatPackage in PB.protobufConfig := true

PB.protobufSettings
