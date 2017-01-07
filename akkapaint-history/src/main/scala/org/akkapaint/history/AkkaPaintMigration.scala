package org.akkapaint.history

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.cassandra.scaladsl.{ CassandraSink, CassandraSource }
import com.datastax.driver.core.{ Cluster, SimpleStatement }

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by liosedhel on 1/7/17.
 */
object AkkaPaintMigration extends App {

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  //#init-mat

  //#init-session
  implicit val session = Cluster.builder.addContactPoint("127.0.0.1").build.connect("akka")

  val stmt = new SimpleStatement("select persistence_id, partition_nr, sequence_nr, timestamp, timebucket from akka.messages where ser_manifest = 'org.akkapaint.proto.Messages.DrawEvent'")

  val update_statement = s"""update messages set tag1='draw_event'
    where persistence_id = ? and partition_nr = ? and sequence_nr = ? and
    timestamp = ? and timebucket = ? """

  val addTagToEvents =
    CassandraSink.apply[PrimaryKey](
      8,
      session.prepare(update_statement),
      (primaryKey, stmt) => {
        stmt.bind(
          primaryKey.persistenceId,
          java.lang.Long.valueOf(primaryKey.partitionNr),
          java.lang.Long.valueOf(primaryKey.sequenceNr),
          primaryKey.timestamp,
          primaryKey.timebucket
        )
      }
    )

  CassandraSource(stmt).map { row =>
    val persistence_id = row.getString("persistence_id")
    val partition_nr = row.getLong("partition_nr")
    val sequence_nr = row.getLong("sequence_nr")
    val timestamp = row.getUUID("timestamp")
    val timebucket = row.getString("timebucket")
    PrimaryKey(persistence_id, partition_nr, sequence_nr, timestamp, timebucket)
  }.runWith(addTagToEvents).recover { case e => e.printStackTrace() }

  case class PrimaryKey(
    persistenceId: String,
    partitionNr: Long,
    sequenceNr: Long,
    timestamp: UUID,
    timebucket: String
  )

}
