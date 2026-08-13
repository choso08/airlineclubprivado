package controllers

import java.nio.file.Path

import com.patson.data.AirlineSource
import javax.imageio.ImageIO

object LogoUtil {
  val logos : scala.collection.mutable.Map[Int, Array[Byte]] = collection.mutable.Map(AirlineSource.loadLogos().toSeq: _*) 
  val blank = getBlankLogo()
  val imageHeight = 12
  val imageWidth = 24
  
  def getLogo(airlineId : Int) : Array[Byte]= {
    logos.get(airlineId) match {
      case Some(logo) => logo
      case None => blank
    }
  }
  
  def saveLogo(airlineId : Int, logo : Array[Byte]) = {
    AirlineSource.saveLogo(airlineId, logo)
    logos.put(airlineId, logo) //update cache
  }
  
  def validateUpload(logoFile : Path) : Option[String] = {
    val imageInputStream = ImageIO.createImageInputStream(logoFile.toFile)
    val readers = ImageIO.getImageReaders(imageInputStream)
    if (!readers.hasNext) {
      return Some("Cannot identify image format")
    }
    val reader = readers.next();
    val format = reader.getFormatName
    if (!format.equalsIgnoreCase("png")) {
      return Some(s"Invalid image format: $format")
    }

    val image = ImageIO.read(logoFile.toFile)
    if (image == null) {
      return Some("Cannot read that image")
    }
    // Deliberately no size check. It used to insist on exactly 24 by 12 and
    // reject anything else, which is a size nobody has an image in - so the
    // usual outcome was an upload that appeared to work, a logo that never
    // changed, and no explanation. The picture is scaled instead; see
    // toLogoSize.
    return None
  }

  /**
   * Scale an uploaded picture to the size the game draws logos at.
   *
   * The logo appears beside the airline name at 24 by 12 pixels and nowhere
   * else, so there is nothing to be gained by insisting the file already be
   * that size and a great deal lost: 24 by 12 is a size no image editor
   * produces by accident, and the rejection came back through an uploader that
   * shows it quietly.
   *
   * Anything already the right size is returned untouched, so nothing is
   * re-encoded for no reason.
   */
  def toLogoSize(source : Path) : Array[Byte] = {
    val image = ImageIO.read(source.toFile)
    if (image.getWidth == imageWidth && image.getHeight == imageHeight) {
      return java.nio.file.Files.readAllBytes(source)
    }

    val scaled = new java.awt.image.BufferedImage(imageWidth, imageHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val graphics = scaled.createGraphics()
    // Bilinear rather than nearest neighbour: shrinking a photograph to 24
    // pixels wide without it is a mess of stray pixels.
    graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
      java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
      java.awt.RenderingHints.VALUE_RENDER_QUALITY)
    graphics.drawImage(image, 0, 0, imageWidth, imageHeight, null)
    graphics.dispose()

    val out = new java.io.ByteArrayOutputStream()
    ImageIO.write(scaled, "png", out)
    out.toByteArray
  }
  
  def getBlankLogo() = {
    //val buffer = new ByteArrayOutputStream();
    val is = play.Environment.simple().resourceAsStream("/logo/blank.png")

    val targetArray = new Array[Byte](is.available());
    is.read(targetArray);

    targetArray
  }
}