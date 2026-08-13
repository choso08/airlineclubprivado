package controllers

import java.nio.file.{Files, Paths}
import scala.util.Try

/**
 * A short string that changes whenever a new build is running.
 *
 * It goes on the end of every script and stylesheet the game loads, so that
 * their addresses change when the game changes and no browser can serve
 * yesterday's code for today's page.
 *
 * The assets are already sent with "no-cache", which asks browsers to check
 * with the server before reusing what they have - and they mostly do. Mostly
 * is the problem: a copy cached under an older rule keeps that rule until it
 * expires, so a change would land some time later, for some people, and the
 * ones who saw it and the ones who did not would be looking at different
 * games. Every round of this ended in somebody being told to press
 * ctrl-shift-R, which is not a fix, it is a workaround with a person in it.
 *
 * An address that changes needs no cooperation from the browser at all.
 *
 * The stamp is the version written by scripts/write-version.sh during an
 * update - so it changes exactly when the code does. Failing that (a checkout
 * that has never been updated, a hand-started server) it falls back to when
 * this process started, which changes on every restart: more re-fetching than
 * strictly needed, and never a stale file.
 */
object BuildStamp {
  val value : String = fromVersionFile.getOrElse(startedAt)

  private def fromVersionFile : Option[String] = {
    val candidates = List(Paths.get("conf/version.json"), Paths.get("airline-web/conf/version.json"))
    candidates.find(Files.exists(_)).flatMap { path =>
      Try {
        val text = new String(Files.readAllBytes(path), "UTF-8")
        // Read with a regex rather than a JSON parser: this is wanted while
        // the first page is being rendered, and a malformed file should cost
        // a fallback stamp rather than a broken page.
        """"version"\s*:\s*"([^"]+)"""".r.findFirstMatchIn(text).map(_.group(1))
      }.toOption.flatten.map(_.trim).filter(v => v.nonEmpty && v != "unknown")
    }
  }

  /** Base 36 to keep it short - it only has to differ, not to be readable. */
  private def startedAt : String =
    java.lang.Long.toString(System.currentTimeMillis() / 1000, 36)
}
