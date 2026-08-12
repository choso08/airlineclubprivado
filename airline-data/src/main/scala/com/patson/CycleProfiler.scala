package com.patson

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * Times each phase of a simulation cycle.
 *
 * A cycle is a dozen phases run back to back, and the only number reported
 * today is the total - "cycle 12 spent 74 secs" - which says nothing about
 * where the time went. Any attempt to make the simulation faster without this
 * is guesswork, and guesswork on a 10,000 line simulation is expensive.
 *
 * Usage, wrapping each phase:
 *
 *   CycleProfiler.phase("links") { LinkSimulation.linkSimulation(...) }
 *
 * At the end of the cycle, `report()` prints a breakdown, slowest first, and
 * the whole thing is cheap enough to leave on permanently: one currentTimeMillis
 * per phase, roughly a dozen per cycle.
 *
 * Set AIRLINE_PROFILE_CYCLES=off to reduce it to plain pass-through calls.
 */
object CycleProfiler {
  private[this] val enabled: Boolean =
    sys.env.getOrElse("AIRLINE_PROFILE_CYCLES", "on").toLowerCase != "off"

  // A cycle runs on one thread today, but the phases inside it use parallel
  // collections, so keep this safe against a phase being timed from elsewhere.
  private[this] val timings = mutable.LinkedHashMap[String, Long]()
  private[this] val history = ListBuffer[Map[String, Long]]()

  /** Number of past cycles to keep for trend reporting. */
  private[this] val HISTORY_LIMIT = 20

  def phase[T](name: String)(block: => T): T = {
    if (!enabled) return block

    val start = System.currentTimeMillis()
    try block
    finally {
      val elapsed = System.currentTimeMillis() - start
      timings.synchronized {
        timings.put(name, timings.getOrElse(name, 0L) + elapsed)
      }
    }
  }

  def reset(): Unit = timings.synchronized(timings.clear())

  /**
   * Print the breakdown for the cycle just finished, and reset for the next.
   * `totalMillis` is passed in rather than summed, so the gap between it and
   * the phases - time spent outside any instrumented phase - is visible too.
   */
  def report(cycle: Int, totalMillis: Long): Unit = {
    if (!enabled) return

    val snapshot = timings.synchronized {
      val copy = timings.toMap
      history += copy
      if (history.size > HISTORY_LIMIT) history.remove(0)
      timings.clear()
      copy
    }

    if (snapshot.isEmpty) return

    val accounted = snapshot.values.sum
    val unaccounted = math.max(0L, totalMillis - accounted)

    val rows = (snapshot.toList :+ ("(everything else)" -> unaccounted))
      .filter(_._2 > 0)
      .sortBy(-_._2)

    val width = rows.map(_._1.length).max
    val pct = (ms: Long) => if (totalMillis <= 0) 0.0 else ms * 100.0 / totalMillis

    println(s"--- cycle $cycle phase breakdown (${totalMillis / 1000.0}s total) ---")
    rows.foreach { case (name, ms) =>
      val bar = "#" * math.max(0, (pct(ms) / 2).round.toInt)   // 50 chars = 100%
      println(f"    ${name.padTo(width, ' ')}  ${ms / 1000.0}%6.1fs  ${pct(ms)}%5.1f%%  $bar")
    }

    // The point of keeping history: whether a phase is drifting upwards as the
    // world fills with routes and aircraft, which is what will eventually bite.
    if (history.size >= 5) {
      val keys = history.flatMap(_.keys).distinct
      val drifting = keys.flatMap { key =>
        val series = history.flatMap(_.get(key)).toList
        if (series.size < 5) None
        else {
          val firstHalf = series.take(series.size / 2)
          val lastHalf = series.drop(series.size / 2)
          val before = firstHalf.sum.toDouble / firstHalf.size
          val after = lastHalf.sum.toDouble / lastHalf.size
          if (before > 500 && after > before * 1.5) Some((key, before, after)) else None
        }
      }
      if (drifting.nonEmpty) {
        println("    growing over the last cycles:")
        drifting.foreach { case (key, before, after) =>
          println(f"      $key: ${before / 1000.0}%.1fs -> ${after / 1000.0}%.1fs")
        }
      }
    }
  }
}
