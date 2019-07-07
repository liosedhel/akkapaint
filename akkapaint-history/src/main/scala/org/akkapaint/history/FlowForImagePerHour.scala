package org.akkapaint.history

import java.nio.ByteBuffer

import akka.NotUsed
import akka.persistence.query.Offset
import akka.stream.alpakka.cassandra.scaladsl.CassandraSink
import akka.stream.scaladsl.{Flow, Sink}
import com.datastax.driver.core.Session
import org.akkapaint.history.ImageAggregationFlowFactory.ImageUpdateEmit
import org.akkapaint.proto.Messages.DrawEvent
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.concurrent.ExecutionContext

object FlowForImagePerHour {
  case class ImageUpdatePerHour(date: String, hour: Int, image: ByteBuffer, emit: Option[ImageUpdatePerHour] = None)
}
case class FlowForImagePerHour()(implicit session: Session, executionContext: ExecutionContext)
    extends ((Offset => Unit) => Sink[(DateTime, DrawEvent, Offset), NotUsed]) {

  private val insert_statement_picture_per_hour =
    s"""INSERT INTO pictures_hours (date, hour, picture) VALUES (?,?,?);""".stripMargin

  val sinkForImagePerHour: Flow[ImageUpdateEmit, ImageUpdateEmit, NotUsed] =
    CassandraFlow.apply[ImageUpdateEmit](
      8,
      session.prepare(insert_statement_picture_per_hour),
      (data, stmt) => {
        val dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
        val timeBucket = dateTimeFormat.print(data.dateTime)
        val hourOfDay: Int = data.dateTime.getHourOfDay
        stmt.bind(timeBucket, new Integer(hourOfDay), data.image)
      }
    )

  override def apply(confirmOffset: (Offset) => Unit): Sink[(DateTime, DrawEvent, Offset), NotUsed] = {
    ImageAggregationFlowFactory()
      .generate(org.joda.time.Hours.ONE)
      .via(sinkForImagePerHour)
      .to(Sink.foreach(t => confirmOffset(t.offset)))
  }
}
