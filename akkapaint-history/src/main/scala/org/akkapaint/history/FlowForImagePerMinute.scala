package org.akkapaint.history

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.imageio.ImageIO

import akka.NotUsed
import akka.stream.alpakka.cassandra.scaladsl.CassandraSink
import akka.stream.scaladsl.Flow
import com.datastax.driver.core.Session
import org.akkapaint.history.FlowForImagePerMinute.{ ImageUpdate, ImageUpdatePerMinute }
import org.akkapaint.proto.Messages.{ DrawEvent, Pixel }
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.concurrent.ExecutionContext

object FlowForImagePerMinute {
  case class ImageUpdate(dateTime: DateTime, image: BufferedImage, emit: Option[ImageUpdatePerMinute] = None)
  case class ImageUpdatePerMinute(date: String, hour: Int, minute: Int, image: ByteBuffer)
}
case class FlowForImagePerMinute()(implicit session: Session, executionContext: ExecutionContext) {

  import AkkaPaintHistoryImageUtils._

  private val insert_statement_picture_per_minute =
    s"""INSERT INTO pictures_minutes (date, hour, minutes, picture) VALUES (?,?,?,?);"""

  val defaultDate = DateTime.now()

  val emitNewPicturePerMinute: Flow[(DateTime, DrawEvent), ImageUpdatePerMinute, NotUsed] =
    Flow[(DateTime, DrawEvent)].scan(
      ImageUpdate(defaultDate, newDefaultImage())
    ) {
        case (ImageUpdate(lastDateTime, image, _), (datetime, d: DrawEvent)) =>
          updateImageAndEmit(image, lastDateTime, datetime, d)
      }
      .collect { case update: ImageUpdate if update.emit.isDefined => update.emit.get }

  val sinkForImagePerMinute =
    CassandraSink.apply[ImageUpdatePerMinute](
      8,
      session.prepare(insert_statement_picture_per_minute),
      (data, stmt) => {
        stmt.bind(data.date, new Integer(data.hour), new Integer(data.minute), data.image)
      }
    )

  private val dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd")

  private def updateImageAndEmit(
    lastImage: BufferedImage,
    lastDateTime: DateTime,
    currentDateTime: DateTime,
    drawEvent: DrawEvent
  ): ImageUpdate = {

    if (lastDateTime != defaultDate && (
      lastDateTime.getDayOfYear != currentDateTime.getDayOfYear ||
      lastDateTime.getMinuteOfDay != currentDateTime.getMinuteOfDay
    )) {
      val hourOfDay: Int = lastDateTime.getHourOfDay
      val minutesOfHour: Int = lastDateTime.getMinuteOfHour
      val baos = new ByteArrayOutputStream()
      ImageIO.write(lastImage, "BMP", baos)
      val byteImage = ByteBuffer.wrap(baos.toByteArray)
      val timeBucket = dateTimeFormat.print(lastDateTime)
      val imagePerMinute = ImageUpdatePerMinute(timeBucket, hourOfDay, minutesOfHour, byteImage)

      //mutate image after generatingByteBuffer with previous image
      val updatedImage = updateImage(lastImage, drawEvent.changes, drawEvent.color)
      ImageUpdate(currentDateTime, updatedImage, emit = Some(imagePerMinute))

    } else {
      val updatedImage = updateImage(lastImage, drawEvent.changes, drawEvent.color)
      ImageUpdate(currentDateTime, updatedImage)
    }
  }

}

object AkkaPaintHistoryImageUtils {
  val maxWidth = 1600
  val maxHeight = 800
  def newDefaultImage(width: Int = maxWidth, height: Int = maxHeight) = {
    new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
  }

  def updateImage(
    res: BufferedImage,
    pixelSeq: Seq[Pixel],
    color: String
  ): BufferedImage = {
    try {
      val a = Color.decode(color)
      pixelSeq
        .filter(p => p.x >= 0 && p.y >= 0 && p.x < maxWidth && p.y < maxHeight)
        .foreach(d => res.setRGB(d.x, d.y, a.getRGB))
      res
    } catch {
      case _: Throwable => res
    }
  }
}
