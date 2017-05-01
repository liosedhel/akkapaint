package org.akkapaint.history

import java.util.UUID

import akka.NotUsed
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery, TimeBasedUUID}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.stream.scaladsl.{Broadcast, GraphDSL, RunnableGraph, Source}
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.util.Timeout
import com.datastax.driver.core.Cluster
import com.typesafe.config.Config
import org.akkapaint.history.AkkaPaintDrawEventProjection.{NewOffsetSaved, SaveNewOffset, SavedAck, Start}
import org.akkapaint.proto.Messages.DrawEvent
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object AkkaPaintDrawEventProjection {

  case object Start
  case class SaveNewOffset(offset: UUID)
  case class NewOffsetSaved(offset: UUID)
  case class SavedAck()
}

case class AkkaPaintDrawEventProjection(
    akkaPaintHistoryConfig: Config
) extends PersistentActor {

  private val logger = LoggerFactory.getLogger(getClass)

  import scala.collection.JavaConverters._

  private implicit val materializer = ActorMaterializer()

  implicit val ec = context.system.dispatcher
  implicit val timeout: Timeout = 30.seconds

  private val readJournal =
    PersistenceQuery(context.system).readJournalFor[CassandraReadJournal](
      CassandraReadJournal.Identifier
    )

  import akka.pattern.ask

  private val originalEventStream: Source[(DateTime, DrawEvent, TimeBasedUUID), NotUsed] = readJournal
    .eventsByTag("draw_event", TimeBasedUUID(readJournal.firstOffset))
    .mapAsync(1) {
      case EventEnvelope(offset @ TimeBasedUUID(time), _, _, d: DrawEvent) =>
        (self ? SaveNewOffset(time)).mapTo[SavedAck].map { _ =>
          val timestamp = new DateTime(UUIDToDate.getTimeFromUUID(time))
          (timestamp, d, offset)
        }
    }

  private val generatingHistoryGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val cassandraCluster =
      Cluster.builder()
        .addContactPoints(akkaPaintHistoryConfig.getStringList("cassandra-journal.contact-points").asScala: _*)
        .build()
    implicit val session = cassandraCluster.connect("akka")

    val flowForImagePerMinute = FlowForImagePerMinute()
    val flowForImagePerHour = FlowForImagePerHour()

    val bcast = builder.add(Broadcast[(DateTime, DrawEvent, TimeBasedUUID)](2))

    bcast ~> flowForImagePerMinute.flow
    originalEventStream ~> bcast ~> flowForImagePerHour.flow
    ClosedShape
  })

  var lastOffset: UUID = readJournal.firstOffset

  self ! Start

  override def receiveCommand: Receive = {
    case Start =>
      logger.info("Running history generation")
      generatingHistoryGraph.run()
    case SaveNewOffset(offset) => persist(NewOffsetSaved(offset)) { event =>
      lastOffset = event.offset
      sender() ! SavedAck()
    }
  }

  override def receiveRecover: Receive = {
    case NewOffsetSaved(offset) => {
      lastOffset = offset
    }
    case RecoveryCompleted => {
      val timestamp = new DateTime(UUIDToDate.getTimeFromUUID(lastOffset)).toString("MM/dd/yyyy HH:mm:ss")
      logger.info(s"Recovering AkkaPaintHistoryGenerator, last offset: $timestamp")
    }
  }

  context.system.scheduler.schedule(1.minutes, 5.minutes)(saveSnapshot(NewOffsetSaved(lastOffset)))

  override def persistenceId: String = "HistoryGenerator"
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

