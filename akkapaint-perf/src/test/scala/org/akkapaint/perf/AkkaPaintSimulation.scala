package org.akkapaint.perf

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{TextMessage, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.akkapaint.perf.utils.ImageUtils
import rapture.json.Json

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import rapture.json.jsonBackends.jawn._

import scala.concurrent.Future

class AkkaPaintSimulation extends Simulation {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val webSocketFlow = Http()
    .webSocketClientFlow(WebSocketRequest("ws://localhost:9000/socket"))

  final case class Pixel(x: Int, y: Int)

  final case class DrawOutput(
    pixels: Seq[Int],
    color: String
  )
  val testDraw = Json(DrawOutput(Seq(0, 0, 0, 1, 1, 1), "#BBBBBB")).toBareString

  val hugeImage = ImageIO.read(getClass.getResource("/anime_big.jpg"))

  val array = ImageUtils.convertTo2DUsingGetRGB(hugeImage)

  Source(array.map(TextMessage.apply).toList).via(webSocketFlow).to(Sink.ignore).run()

  val conf =
    http.baseURL("http://localhost:9000").wsBaseURL("ws://localhost:9000")

  val scn = scenario("Gatling")
    .exec(ws("index").open("/socket"))
    .pause(1)
    .foreach(array.toSeq, "draw") { exec({ ws("index").sendText("${draw}") }) }

  setUp(scn.inject(atOnceUsers(2)))
    .protocols(conf)

}
