package org.akkapaint.shard.cluster

import akka.actor._
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings, ShardRegion }
import akka.serialization.Serialization
import com.typesafe.config.Config
import org.akkapaint.proto.Messages._
import org.akkapaint.shard.actors.AkkaPaintActor
import BoardShardUtils.{ ShardingUnregister, ShardingRegister }

object BoardShardUtils {

  case class ShardingRegister(shardId: Int, entityId: Int, client: ActorRef)
  case class ShardingUnregister(shardId: Int, entityId: Int, client: ActorRef)

  case class ShardingPixelsUtil(entitySize: Int, width: Int, height: Int) {

    val shardNumber = height / entitySize
    val entityNumber = width / entitySize

    def shardingPixels(changes: Iterable[Pixel], color: String): Iterable[DrawEntity] = {
      changes.groupBy { pixel =>
        (pixel.y / entitySize, pixel.x / entitySize)
      }.map {
        case ((shardId, entityId), pixels) =>
          DrawEntity(shardId, entityId, pixels.toSeq, color)
      }.toIterable
    }

    def registerToAll(self: ActorRef): Iterable[ShardingRegister] = {
      for {
        shardId <- 0 until shardNumber
        entityId <- 0 until entityNumber
      } yield ShardingRegister(shardId, entityId, self)
    }

    def unregisterFromAll(self: ActorRef): Iterable[ShardingUnregister] = {
      for {
        shardId <- 0 until shardNumber
        entityId <- 0 until entityNumber
      } yield ShardingUnregister(shardId, entityId, self)
    }
  }

}

class AkkaPaintShardingCluster {

  val regionName = "AkkaPaintShard"

  val entitySize = 100 //one entity area is equal to entitySize X entitySize

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case DrawEntity(_, entityId, pixels, color) ⇒ (entityId.toString, Draw(pixels, color))
    case ShardingRegister(_, entityId, client) ⇒ (entityId.toString, RegisterClient(Serialization.serializedActorPath(client)))
    case ShardingUnregister(_, entityId, client) ⇒ (entityId.toString, UnregisterClient(Serialization.serializedActorPath(client)))
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case DrawEntity(shardId, _, _, _) ⇒ shardId.toString
    case ShardingRegister(shardId, _, _) ⇒ shardId.toString
    case ShardingUnregister(shardId, _, _) ⇒ shardId.toString
  }

  def initializeClusterSharding(actorSystemConf: Config): ActorSystem = {

    val system = ActorSystem("AkkaPaintSystem", actorSystemConf)

    ClusterSharding(system).start(
      typeName = regionName,
      entityProps = Props[AkkaPaintActor],
      settings = ClusterShardingSettings(system),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
    system
  }

  def shardRegion()(implicit actorSystem: ActorSystem) = {
    ClusterSharding(actorSystem).shardRegion(regionName)
  }

}
