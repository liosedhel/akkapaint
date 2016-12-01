Akka Paint [![Build Status](https://travis-ci.org/liosedhel/akkapaint.svg?branch=master)](https://travis-ci.org/liosedhel/akkapaint)
=================================
Akka Paint is web application that implements scalable, multiuser, with real time changes painting board.
This is a simple project demonstrating Play! and Akka features such as:

* persistent actors
* cluster sharding
* akka-streams

More info about the project can be found [here](http://virtuslab.com/blog/akkapaint-simplicity-and-power-of-akka/).

Try it!
===========

* Install and run [cassandra](http://cassandra.apache.org/) database or change the `akkapaint-web.conf` file for other database (e.g. [in memory database](https://github.com/dnvriend/akka-persistence-inmemory))
* Simply type `sbt run` and go to the address [http://localhost:9000/demo](http://localhost:9000/demo).

Open the second window to see real time changes!

Or try the online demo: [http://demo.akkapaint.org/](http://demo.akkapaint.org/)

Load the whole image
===========
Type `sbt "runMain org.akkapaint.perf.AkkaPaintSimulationMain"` to load default image. It will send whole image pixel by pixel (aka. stress test ;))

