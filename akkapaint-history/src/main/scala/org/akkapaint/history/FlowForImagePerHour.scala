package org.akkapaint.history

import java.nio.ByteBuffer

import akka.stream.alpakka.cassandra.scaladsl.CassandraSink
import akka.stream.scaladsl.Flow
import com.datastax.driver.core.Session
import org.akkapaint.history.FlowForImagePerHour.ImageUpdatePerHour
import org.akkapaint.history.FlowForImagePerMinute.ImageUpdatePerMinute

import scala.concurrent.ExecutionContext

object FlowForImagePerHour {
  case class ImageUpdatePerHour(date: String, hour: Int, image: ByteBuffer, emit: Option[ImageUpdatePerHour] = None)
}
case class FlowForImagePerHour()(implicit session: Session, executionContext: ExecutionContext) {

  private val insert_statement_picture_per_hour =
    s"""INSERT INTO pictures_hours (date, hour, picture) VALUES (?,?,?);""".stripMargin

  val defualtDate = "DefaultDate"

  val emitNewPicturePerHour = Flow[ImageUpdatePerMinute].scan(
    ImageUpdatePerHour(defualtDate, 0, ByteBuffer.wrap(Array.emptyByteArray))
  ) {
      case (ImageUpdatePerHour(lastDate, lastHour, lastImage, _), ImageUpdatePerMinute(date, hour, _, image)) =>
        if (lastDate != defualtDate && (lastDate != date || lastHour != hour))
          ImageUpdatePerHour(date, hour, image, emit = Some(ImageUpdatePerHour(lastDate, lastHour, lastImage)))
        else
          ImageUpdatePerHour(date, hour, image)
    }.collect { case update: ImageUpdatePerHour if update.emit.isDefined => update.emit.get }

  val sinkForImagePerHour =
    CassandraSink.apply[ImageUpdatePerHour](
      8,
      session.prepare(insert_statement_picture_per_hour),
      (data, stmt) => {
        stmt.bind(data.date, new Integer(data.hour), data.image)
      }
    )
}