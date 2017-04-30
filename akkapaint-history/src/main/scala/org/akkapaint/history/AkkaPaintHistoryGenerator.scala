package org.akkapaint.history

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{ EventEnvelope, PersistenceQuery, TimeBasedUUID }
import akka.stream.scaladsl.{ Broadcast, GraphDSL, RunnableGraph }
import akka.stream.{ ActorMaterializer, ClosedShape }
import com.datastax.driver.core.Cluster
import com.typesafe.config.ConfigFactory
import org.akkapaint.history.FlowForImagePerMinute.ImageUpdatePerMinute
import org.akkapaint.proto.Messages.DrawEvent
import org.joda.time.DateTime

object AkkaPaintHistoryGenerator extends App {

  new AkkaPaintHistoryGenerator().run()

}

case class AkkaPaintHistoryGenerator() {

  import scala.collection.JavaConverters._
  private val akkaPaintHistoryConfig = ConfigFactory.load("akkapaint-history.conf")
  private implicit val actorSystem = ActorSystem("GeneratingHistorySytem", akkaPaintHistoryConfig)
  private implicit val materializer = ActorMaterializer()

  private val cluster =
    Cluster.builder()
      .addContactPoints(akkaPaintHistoryConfig.getStringList("cassandra-journal.contact-points").asScala: _*)
      .build()

  implicit val session = cluster.connect("akka")
  implicit val ec = actorSystem.dispatcher

  private val readJournal =
    PersistenceQuery(actorSystem).readJournalFor[CassandraReadJournal](
      CassandraReadJournal.Identifier
    )
  private val originalEventStream = readJournal
    .eventsByTag("draw_event", readJournal.timeBasedUUIDFrom(DateTime.now().minusYears(5).getMillis))
    .map {
      case EventEnvelope(TimeBasedUUID(time), _, _, d: DrawEvent) =>
        val timestamp = new DateTime(UUIDToDate.getTimeFromUUID(time))
        (timestamp, d)
    }

  private val generatingHistoryGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val flowForImagePerMinute = FlowForImagePerMinute()
    val flowForImagePerHour = FlowForImagePerHour()
    val sinkAllPicturesList = SinkAllPicturesList()
    import flowForImagePerHour._
    import flowForImagePerMinute._
    import sinkAllPicturesList._

    val bcast = builder.add(Broadcast[ImageUpdatePerMinute](3))

    bcast ~> emitNewPicturePerHour ~> sinkForImagePerHour
    originalEventStream ~> emitNewPicturePerMinute ~> bcast ~> sinkForImagePerMinute
    bcast ~> sinkSaveChangesList
    ClosedShape
  })

  def run() = {
    println("Running history generation")
    generatingHistoryGraph.run()
  }

}

import java.util.UUID

object UUIDToDate {
  // This method comes from Hector's TimeUUIDUtils class:
  // https://github.com/rantav/hector/blob/master/core/src/main/java/me/prettyprint/cassandra/utils/TimeUUIDUtils.java
  val NUM_100NS_INTERVALS_SINCE_UUID_EPOCH = 0x01b21dd213814000L

  def getTimeFromUUID(uuid: UUID): Long = {
    (uuid.timestamp() - NUM_100NS_INTERVALS_SINCE_UUID_EPOCH) / 10000
  }
}

