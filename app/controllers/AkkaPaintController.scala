package controllers

import akka.stream.ActorMaterializer
import com.google.inject.Inject
import com.typesafe.config.ConfigFactory
import org.akkapaint.shard.cluster.{ AkkaPaintShardingCluster, BoardShardUtils }
import BoardShardUtils.ShardingPixelsUtil
import org.akkapaint.proto.Messages.{ ChangesOutput, Draw, Pixel }
import play.api.libs.json.Json
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc.{ Action, Controller, WebSocket }
import services.ClientConnection

class AkkaPaintController @Inject() (akkaPaintShardingCluster: AkkaPaintShardingCluster) extends Controller {

  // Create an Akka system
  implicit val system = akkaPaintShardingCluster.initializeClusterSharding(ConfigFactory.load("akkapaint-web.conf"))

  implicit val flowMaterializer = ActorMaterializer()

  implicit val pixelFormat = Json.format[Pixel]
  implicit val boardUpdatedFormat = Json.format[ChangesOutput]
  implicit val drawFormat = Json.format[Draw]
  implicit val messageFlowTransformer = MessageFlowTransformer.jsonMessageFlowTransformer[ChangesOutput, ChangesOutput]

  def socket = WebSocket.accept[ChangesOutput, ChangesOutput](requestHeader => {
    ActorFlow.actorRef[ChangesOutput, ChangesOutput](output =>
      ClientConnection.props(
        output,
        akkaPaintShardingCluster.shardRegion(),
        new ShardingPixelsUtil(akkaPaintShardingCluster.entitySize, 1600, 800)
      ), bufferSize = 1600 * 800)
  })

  def index = Action {
    Ok(views.html.draw())
  }

}
