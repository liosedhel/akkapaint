package org.akkapaint.history

import akka.NotUsed
import akka.persistence.query.Offset
import akka.stream.scaladsl.{ Flow, Sink }
import com.datastax.driver.core.Session
import org.akkapaint.history.ImageAggregationFlowFactory.ImageUpdateEmit
import org.akkapaint.proto.Messages.DrawEvent
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.concurrent.ExecutionContext

case class FlowForImagePerMinute()(implicit session: Session, executionContext: ExecutionContext)
    extends ((Offset => Unit) => Sink[(DateTime, DrawEvent, Offset), NotUsed]) {

  private val insert_statement_picture_per_minute =
    s"""INSERT INTO pictures_minutes (date, hour, minutes, picture) VALUES (?,?,?,?);"""

  private val flowForImagePerMinute =
    CassandraFlow.apply[ImageUpdateEmit](
      8,
      session.prepare(insert_statement_picture_per_minute),
      (data, stmt) => {
        val dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
        val timeBucket = dateTimeFormat.print(data.dateTime)
        val hourOfDay: Int = data.dateTime.getHourOfDay
        val minutesOfHour: Int = data.dateTime.getMinuteOfHour
        stmt.bind(timeBucket, new Integer(hourOfDay), new Integer(minutesOfHour), data.image)
      }
    )

  private val insert_statement_changes_list =
    s"""INSERT INTO changes (year, date, hour, minutes) VALUES (?,?,?,?);"""

  private val flowForSaveChangesList = CassandraFlow.apply[ImageUpdateEmit](
    8,
    session.prepare(insert_statement_changes_list),
    (data, stmt) => {
      val dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
      val timeBucket = dateTimeFormat.print(data.dateTime)
      val hourOfDay: Int = data.dateTime.getHourOfDay
      val minutesOfHour: Int = data.dateTime.getMinuteOfHour
      stmt.bind(new Integer(timeBucket.split("-")(0)), timeBucket, new Integer(hourOfDay), new Integer(minutesOfHour))
    }
  )

  override def apply(commitOffset: (Offset) => Unit): Sink[(DateTime, DrawEvent, Offset), NotUsed] = {
    ImageAggregationFlowFactory().generate(org.joda.time.Minutes.ONE)
      .via(flowForImagePerMinute)
      .via(flowForSaveChangesList)
      .to(Sink.foreach(t => commitOffset(t.offset)))
  }
}

