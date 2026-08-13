package controllers

import java.nio.file.{Files, Paths}
import javax.inject._
import play.api.mvc._
import play.api.libs.json.Json

import scala.util.Try

/**
 * Tells the browser whether this instance is about to restart, and whether it
 * already has.
 *
 * Players otherwise have no idea an update is coming: the page simply stops
 * working mid-click and they have to guess that a refresh will fix it. The
 * client polls this, shows a countdown, and reloads itself once the new
 * instance answers.
 */
@Singleton
class InstanceStatusApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  // Changes on every restart, which is exactly what the client watches for.
  // No build id needed - process start time is enough to tell "same server"
  // from "it came back".
  private[this] val startedAt: Long = System.currentTimeMillis()

  // scripts/update.sh writes the epoch millis of the planned restart here
  // before it starts building, and removes it afterwards. A file keeps the
  // two sides decoupled: no endpoint to secure, and nothing to clean up if
  // the update is abandoned half way.
  // A restart announced longer ago than this is treated as abandoned.
  private[this] val STALE_AFTER_MS = 5 * 60 * 1000

  private[this] val restartFlag = Paths.get(
    sys.env.getOrElse("AIRLINE_RESTART_FLAG", "/tmp/airline-restart-at"))

  // Written by scripts/write-version.sh during an update: which build is
  // running and what the last update brought. Read once at startup, since it
  // cannot change without a restart.
  private[this] val versionJson : play.api.libs.json.JsValue = {
    val candidates = List(Paths.get("conf/version.json"), Paths.get("airline-web/conf/version.json"))
    candidates.find(Files.exists(_)) match {
      case Some(path) =>
        Try(Json.parse(new String(Files.readAllBytes(path), "UTF-8")))
          .getOrElse(Json.obj("version" -> "unknown", "changes" -> Json.arr()))
      case None => Json.obj("version" -> "unknown", "changes" -> Json.arr())
    }
  }

  /** What is running, and what the last update changed. */
  def version = Action {
    Ok(versionJson).withHeaders(CACHE_CONTROL -> "no-store")
  }

  def status = Action {
    val now = System.currentTimeMillis()

    val restartAt: Option[Long] =
      if (Files.exists(restartFlag)) {
        Try(new String(Files.readAllBytes(restartFlag)).trim.toLong).toOption
          // Ignore a stale flag. update.sh removes it after restarting, but if
          // an update is interrupted the file survives, and without this every
          // player would sit under a countdown banner for an update that is
          // never coming.
          .filter(_ > now - STALE_AFTER_MS)
      } else None

    Ok(Json.obj(
      "startedAt" -> startedAt,
      "restartAt" -> restartAt,
      "serverNow" -> now,
      "version" -> ((versionJson \ "version").asOpt[String].getOrElse("unknown") : String)
    )).withHeaders(
      // Must never be cached: a stale answer defeats the whole point.
      CACHE_CONTROL -> "no-store, no-cache, must-revalidate",
      PRAGMA -> "no-cache"
    )
  }
}
