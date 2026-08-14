package controllers

import com.patson.data.{AchievementSource, CycleSource}
import com.patson.model.Achievement
import javax.inject.Inject
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc._

/**
  * The shared board of firsts.
  *
  * Every milestone is returned, claimed or not, in the catalogue's order -
  * the empty ones are the point of looking, so the page can show what is
  * still there to be taken.
  */
class AchievementApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  def getAchievements() = Action { implicit request =>
    val claimedById = AchievementSource.loadAll().map(record => (record.achievement.id, record)).toMap
    val currentCycle = CycleSource.loadCycle()

    val entries = Achievement.all.map { achievement =>
      val base = Json.obj(
        "id" -> achievement.id,
        "title" -> achievement.title,
        "description" -> achievement.description)

      claimedById.get(achievement.id) match {
        case Some(record) =>
          base ++ Json.obj(
            "claimed" -> true,
            "airlineId" -> record.airlineId,
            "airlineName" -> record.airlineName,
            "cycle" -> record.cycle,
            "weeksAgo" -> Math.max(0, currentCycle - record.cycle))
        case None =>
          base ++ Json.obj("claimed" -> false)
      }
    }

    Ok(Json.obj("achievements" -> Json.toJson(entries)))
  }
}
