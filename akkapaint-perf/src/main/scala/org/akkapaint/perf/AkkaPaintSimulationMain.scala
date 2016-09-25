package org.akkapaint.perf

import javax.imageio.ImageIO

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{ TextMessage, WebSocketRequest }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import org.akkapaint.perf.utils.ImageUtils

object AkkaPaintSimulationMain extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val webSocketFlow = Http()
    .webSocketClientFlow(WebSocketRequest("ws://liosedhel.pl:9000/socket"))

  val hugeImage = ImageIO.read(getClass.getResource("/horses.jpg"))

  val imageInJson = ImageUtils.convertTo2DUsingGetRGB(hugeImage)

  Source(imageInJson.toList).map(TextMessage.apply).via(webSocketFlow).to(Sink.ignore).run()

}
