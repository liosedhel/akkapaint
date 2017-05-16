package org.akkapaint.history

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.imageio.ImageIO

import akka.NotUsed
import akka.persistence.query.Offset
import akka.stream.scaladsl.Flow
import org.akkapaint.history.ImageAggregationFlowFactory.{ ImageUpdate, ImageUpdateEmit }
import org.akkapaint.proto.Messages.{ DrawEvent, Pixel }
import org.joda.time.{ DateTime, ReadablePeriod }

import scala.concurrent.ExecutionContext

object ImageAggregationFlowFactory {
  case class ImageUpdate(lastEmitDateTime: DateTime, image: BufferedImage, offset: Offset, emit: Option[ImageUpdateEmit] = None)
  case class ImageUpdateEmit(dateTime: DateTime, image: ByteBuffer, offset: Offset)
}

case class ImageAggregationFlowFactory()(implicit executionContext: ExecutionContext) {

  import AkkaPaintHistoryImageUtils._

  val defaultDate = DateTime.now().minusYears(10)

  import javax.imageio.ImageIO

  def generate(timeSlot: ReadablePeriod): Flow[(DateTime, DrawEvent, Offset), ImageUpdateEmit, NotUsed] =
    Flow[(DateTime, DrawEvent, Offset)].scan(
      ImageUpdate(defaultDate, newDefaultImage(), offset = Offset.noOffset)
    ) {
        case (ImageUpdate(lastEmitDateTime, image, _, _), (datetime, d: DrawEvent, offset)) =>
          updateImageAndEmit(image, offset, lastEmitDateTime, datetime, d, timeSlot)
      }
      .collect { case update: ImageUpdate if update.emit.isDefined => update.emit.get }

  private def updateImageAndEmit(
    lastImage: BufferedImage,
    offset: Offset,
    lastEmitDateTime: DateTime,
    currentDateTime: DateTime,
    drawEvent: DrawEvent,
    timeSlot: ReadablePeriod
  ): ImageUpdate = {

    if (currentDateTime.minus(timeSlot).compareTo(lastEmitDateTime) > 0) {
      val baos = new ByteArrayOutputStream()
      ImageIO.write(lastImage, "jpeg", baos)
      val byteImage = ByteBuffer.wrap(baos.toByteArray)
      val imageGeneratedWithinTimeSlot = ImageUpdateEmit(currentDateTime, byteImage, offset)

      //mutate image after generatingByteBuffer with previous image
      val updatedImage = updateImage(lastImage, drawEvent.changes, drawEvent.color)
      ImageUpdate(currentDateTime, updatedImage, offset, emit = Some(imageGeneratedWithinTimeSlot))

    } else {
      val updatedImage = updateImage(lastImage, drawEvent.changes, drawEvent.color)
      ImageUpdate(lastEmitDateTime, updatedImage, offset)
    }
  }

}

object AkkaPaintHistoryImageUtils {
  val maxWidth = 1600
  val maxHeight = 800
  def newDefaultImage(width: Int = maxWidth, height: Int = maxHeight) = {
    val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()

    graphics.setPaint(new Color(255, 255, 255)) //set background color to white
    graphics.fillRect(0, 0, width, height)
    image
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
