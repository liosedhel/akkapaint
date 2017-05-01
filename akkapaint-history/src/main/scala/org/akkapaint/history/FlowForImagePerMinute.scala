package org.akkapaint.history

import akka.persistence.query.Offset
import akka.stream.alpakka.cassandra.scaladsl.CassandraSink
import akka.stream.scaladsl.Sink
import akka.{ Done, NotUsed }
import com.datastax.driver.core.Session
import org.akkapaint.history.ImageAggregationFlowFactory.ImageUpdateEmit
import org.akkapaint.proto.Messages.DrawEvent
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.concurrent.{ ExecutionContext, Future }

case class FlowForImagePerMinute()(implicit session: Session, executionContext: ExecutionContext) {

  private val insert_statement_picture_per_minute =
    s"""INSERT INTO pictures_minutes (date, hour, minutes, picture) VALUES (?,?,?,?);"""

  private val sinkForImagePerMinute: Sink[ImageUpdateEmit, Future[Done]] =
    CassandraSink.apply[ImageUpdateEmit](
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

  private val sinkSaveChangesList = CassandraSink.apply[ImageUpdateEmit](
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

  val flow: Sink[(DateTime, DrawEvent, Offset), NotUsed] = {

    ImageAggregationFlowFactory().generate(org.joda.time.Minutes.ONE)
      .alsoTo(sinkSaveChangesList)
      .to(sinkForImagePerMinute)
  }
}

