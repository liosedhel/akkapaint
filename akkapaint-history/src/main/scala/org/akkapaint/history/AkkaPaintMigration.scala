package org.akkapaint.history

import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.alpakka.cassandra.scaladsl.CassandraSource
import akka.stream.scaladsl.{Flow, Keep, Sink}
import com.datastax.driver.core._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

object AkkaPaintMigration extends App {

  import akka.stream.ActorAttributes.supervisionStrategy
  import Supervision.resumingDecider

  implicit val system = ActorSystem()
  val decider: Supervision.Decider = {
    case _ => Supervision.Resume
  }
  implicit val mat = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))
  //#init-mat

  //#init-session
  implicit val session = Cluster.builder.addContactPoint("127.0.0.1").build.connect("akka")

  val stmt = new SimpleStatement("select persistence_id, partition_nr, sequence_nr, timestamp, timebucket from akka.messages where ser_manifest = 'org.akkapaint.proto.Messages.DrawEvent'")

  val update_statement = s"""update messages set tag1='draw_event'
    where persistence_id = ? and partition_nr = ? and sequence_nr = ? and
    timestamp = ? and timebucket = ? """

  val addTagToEvents =
    CassandraSinkSynchronous.apply[PrimaryKey](
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
    ).withAttributes(supervisionStrategy(resumingDecider))

  CassandraSource(stmt).withAttributes(supervisionStrategy(resumingDecider)).map { row =>
    val persistence_id = row.getString("persistence_id")
    val partition_nr = row.getLong("partition_nr")
    val sequence_nr = row.getLong("sequence_nr")
    val timestamp = row.getUUID("timestamp")
    val timebucket = row.getString("timebucket")
    PrimaryKey(persistence_id, partition_nr, sequence_nr, timestamp, timebucket)
  }.withAttributes(supervisionStrategy(resumingDecider))
    .runWith(addTagToEvents).recover { case e => e.printStackTrace() }

  case class PrimaryKey(
    persistenceId: String,
    partitionNr: Long,
    sequenceNr: Long,
    timestamp: UUID,
    timebucket: String
  )

  object CassandraSinkSynchronous {
    def apply[T](statement: PreparedStatement,
                 statementBinder: (T, PreparedStatement) => BoundStatement)(
                  implicit session: Session,
                  ex: ExecutionContext): Sink[T, Future[Done]] =
      Flow[T]
        .map(t ⇒ session.execute(statementBinder(t, statement)))
        .toMat(Sink.ignore)(Keep.right)
  }
}
