package org.akkapaint.history

import java.nio.ByteBuffer

import akka.stream.alpakka.cassandra.scaladsl.CassandraSink
import com.datastax.driver.core.Session
import org.akkapaint.history.ImageAggregationFlowFactory.ImageUpdateEmit
import org.joda.time.format.DateTimeFormat

import scala.concurrent.ExecutionContext

object FlowForImagePerHour {
  case class ImageUpdatePerHour(date: String, hour: Int, image: ByteBuffer, emit: Option[ImageUpdatePerHour] = None)
}
case class FlowForImagePerHour()(implicit session: Session, executionContext: ExecutionContext) {

  private val insert_statement_picture_per_hour =
    s"""INSERT INTO pictures_hours (date, hour, picture) VALUES (?,?,?);""".stripMargin

  val sinkForImagePerHour =
    CassandraSink.apply[ImageUpdateEmit](
      8,
      session.prepare(insert_statement_picture_per_hour),
      (data, stmt) => {
        val dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
        val timeBucket = dateTimeFormat.print(data.dateTime)
        val hourOfDay: Int = data.dateTime.getHourOfDay
        stmt.bind(timeBucket, new Integer(hourOfDay), data.image)
      }
    )

  val flow = ImageAggregationFlowFactory().generate(org.joda.time.Hours.ONE).to(sinkForImagePerHour)
}