package services

import akka.actor.{Actor, ActorRef, Props}
import akka.serialization.Serialization
import org.akkapaint.shard.cluster.BoardShardUtils
import org.akkapaint.proto.Messages._
import BoardShardUtils.ShardingPixelsUtil
import services.ClientConnection.GetChanges
import scala.collection.mutable

object ClientConnection {

  case class GetChanges(destination: ActorRef)

  def props(output: ActorRef, akkaPaintActor: ActorRef, shardingUtils: ShardingPixelsUtil) =
    Props(new ClientConnection(output, akkaPaintActor, shardingUtils))
}

class ClientConnection(
  output: ActorRef,
  akkaPaintRegion: ActorRef,
  shardingUtils: ShardingPixelsUtil
) extends Actor {

  import scala.concurrent.duration._
  implicit val executionContext = context.dispatcher
  val sendingPixelsExecutionContext = executionContext
  context.system.scheduler.schedule(0.seconds, 1.seconds, self, GetChanges(output))

  shardingUtils.registerToAll(self).foreach(akkaPaintRegion ! _)

  val recentChanges = mutable.Map.empty[Pixel, String]

  override def receive: Receive = {
    case d: ChangesOutput =>
      shardingUtils
        .shardingPixels(
          d.pixels.grouped(2).map(pixels => Pixel(pixels(0), pixels(1))).toSeq,
          d.color
        )
        .foreach(akkaPaintRegion ! _)
    case Changes(changes, color) =>
      updateState(changes, color)
    case GetChanges(destination) =>
      if (recentChanges.nonEmpty) {
        recentChanges
          .groupBy { case (pixel, color) => color }
          .par
          .map {
            case (color, pixelMap) =>
              val pixels = mutable.ListBuffer.empty[Int]
              pixelMap.keys.foreach(pixel => { pixels += pixel.x; pixels += pixel.y; })
              ChangesOutput(pixels, color)
          }
          .foreach(output ! _)
        recentChanges.clear()
      }
    case ReRegisterClient() => sender() ! RegisterClient(Serialization.serializedActorPath(self))
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit =
    shardingUtils.unregisterFromAll(self).foreach(akkaPaintRegion ! _)

  def updateState(changes: Iterable[Pixel], color: String) =
    changes.foreach(pixel => recentChanges.put(pixel, color))
}
