package org.akkapaint.perf.utils

import java.awt.image.BufferedImage

import rapture.json.Json

import scala.collection.mutable
import rapture.json.jsonBackends.jawn._

import scala.collection.mutable.ListBuffer

object ImageUtils {

  final case class Pixel(x: Int, y: Int)

  final case class DrawOutput(
    pixels: Seq[Int],
    color: String
  )

  def convertTo2DUsingGetRGB(image: BufferedImage): Iterable[String] = {
    val width = image.getWidth()
    val height = image.getHeight()
    val colorToPixelsMap = mutable.Map.empty[String, mutable.ListBuffer[Int]]

    for (row <- 0 until height) {
      for (col <- 0 until width) {
        val hex = s"#${Integer.toHexString(image.getRGB(col, row)).substring(2)}"
        val updatedPixelList =
          colorToPixelsMap.get(hex).map(pixels => { pixels += row; pixels += col; pixels; })
            .getOrElse(ListBuffer(row, col))

        colorToPixelsMap.put(hex, updatedPixelList)
      }
    }

    colorToPixelsMap.map { case (color, pixels) => Json(DrawOutput(pixels, color)).toBareString }
  }

}
