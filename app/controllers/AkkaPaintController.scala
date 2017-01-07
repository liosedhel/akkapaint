package controllers

import java.io.ByteArrayInputStream

import akka.stream.ActorMaterializer
import com.datastax.driver.core.{ Cluster, SimpleStatement }
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
import akka.stream.alpakka.cassandra.scaladsl._
import play.api.libs.json._
import org.joda.time.format.DateTimeFormat
import scala.collection.JavaConverters._

class AkkaPaintController @Inject() (akkaPaintShardingCluster: AkkaPaintShardingCluster) extends Controller {

  // Create an Akka system
  val akkaPaintWebConfig = ConfigFactory.load("akkapaint-web.conf")
  implicit val system = akkaPaintShardingCluster.initializeClusterSharding(akkaPaintWebConfig)
  private val cluster =
    Cluster.builder()
      .addContactPoints(akkaPaintWebConfig.getStringList("cassandra-journal.contact-points").asScala: _*)
      .build()

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

  implicit val session = cluster.connect("akka")
  val dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
  import scala.collection.JavaConverters._

  case class ImageHistory(year: Int, date: String, hour: Int, minutes: Int)
  object ImageHistory {
    implicit val format = Json.format[ImageHistory]
  }

  def imageHistoryList = Action {
    val results = new SimpleStatement("SELECT * FROM changes");
    val r = session.execute(results).asScala
      .map(row =>
        ImageHistory(
          row.getInt("year"),
          row.getString("date"),
          row.getInt("hour"),
          row.getInt("minutes")
        )).toList
    Ok(Json.toJson(r))
  }

  val getPicturesPerHours = session.prepare(s"SELECT * FROM pictures_hours where date = ? and hour = ?")
  def getImage(date: String, hour: Int) = Action {
    session.execute(getPicturesPerHours.bind(date, new Integer(hour))).asScala
      .map(row => row.getBytes("picture")).headOption match {
        case Some(imageBytes) => Ok(imageBytes.array()).as("image/bmp")
        case None => NotFound
      }
  }

  val getPicturesPerMinute = session.prepare(s"SELECT * FROM pictures_minutes where date = ? and hour = ? and minutes = ?")
  def getImagePerMinute(date: String, hour: Int, minute: Int) = Action {
    session.execute(getPicturesPerMinute.bind(date, new Integer(hour), new Integer(minute))).asScala
      .map(row => row.getBytes("picture")).headOption match {
        case Some(imageBytes) => Ok(imageBytes.array()).as("image/bmp")
        case None => NotFound
      }
  }

}
