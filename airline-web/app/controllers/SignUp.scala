package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

import javax.inject._
import views._
import models._
import com.patson.data.UserSource
import com.patson.model._
import com.patson.Authentication

import java.util.Calendar
import com.patson.data.AirlineSource
import com.patson.util.AirlineCache
import play.api.libs.ws.WSClient

import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import play.api.libs.json.Writes
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import play.api.libs.json.JsNumber
import play.api.libs.json.JsString

class SignUp @Inject()(cc: ControllerComponents, configuration: play.api.Configuration)(ws: WSClient) extends AbstractController(cc) with play.api.i18n.I18nSupport {
  private[this] val recaptchaUrl = "https://www.google.com/recaptcha/api/siteverify"
  private[this] val recaptchaAction = "signup"
  // The upstream keys below are registered for airline-club.com. A self-hosted
  // instance reached under any other hostname (a Tailscale name, a LAN IP)
  // gets its tokens rejected, which makes registration impossible - so private
  // instances set recaptcha.enabled = false and skip verification entirely.
  // Keep it enabled, with your own keys, if you ever expose signup publicly.
  private[this] val recaptchaEnabled = configuration.getOptional[Boolean]("recaptcha.enabled").getOrElse(true)
  private[this] val recaptchaSecret = configuration.getOptional[String]("recaptcha.secret").getOrElse("6LespV8UAAAAAErZ7LWP51SWmYaYrnAz6Z61jKBC")
  private[this] val recaptchaSiteKey = configuration.getOptional[String]("recaptcha.siteKey").getOrElse("6LespV8UAAAAAJkCUpR8_uNC3P-wZGq7vnTNKEZe")
  private[this] val recaptchaScoreThreshold = 0.5

  // Minimum password length at signup. Upstream hardcoded 6 here while the
  // change-password form accepted 4, so the two disagreed. Now both read this
  // setting - override with AIRLINE_MIN_PASSWORD_LENGTH.
  private[this] val minPasswordLength = configuration.getOptional[Int]("account.minPasswordLength").getOrElse(4)

  /**
   * Sign Up Form definition.
   *
   * Once defined it handle automatically, ,
   * validation, submission, errors, redisplaying, ...
   */
  val signupForm: Form[NewUser] = Form(
    
    // Define a mapping that will handle User values
    mapping(
      "username" -> text(minLength = 4, maxLength = 20).verifying(
        "username can only contain alphanumeric characters",
        userName => userName.forall(char => char.isLetterOrDigit && char <= 'z')).verifying(
        "This username is not available",
        userName => !UserSource.loadUsersByCriteria(List.empty).map { _.userName.toLowerCase() }.contains(userName.toLowerCase())    
      ),
      "email" -> email,
      // Create a tuple mapping for the password/confirm
      "password" -> tuple(
        "main" -> text(minLength = minPasswordLength),
        "confirm" -> text
      ).verifying(
        // Add an additional constraint: both passwords must match
        "Passwords don't match", passwords => passwords._1 == passwords._2
      ),
      "recaptchaToken" -> text,
      "airlineName" -> text(minLength = AirlineUtil.MIN_AIRLINE_NAME_LENGTH, maxLength = AirlineUtil.MAX_AIRLINE_NAME_LENGTH).verifying(
        "Airline name can only contain space and characters",
        airlineName => airlineName.forall(char => (char.isLetter && char <= 'z')  || char == ' ') && !"".equals(airlineName.trim())).verifying(
        "This airline name is not available",
        airlineName => !AirlineSource.loadAllAirlines(false).map { _.name.toLowerCase().replaceAll("\\s", "") }.contains(airlineName.replaceAll("\\s", "").toLowerCase())
      )
    )
    // The mapping signature doesn't match the User case class signature,
    // so we have to define custom binding/unbinding functions
    {
      // Binding: Create a User from the mapping result (ignore the second password and the accept field)
      (username, email, passwords, recaptureToken, airlineName) => NewUser(username.trim, passwords._1, email.trim, recaptureToken, airlineName.trim)
    } 
    {
      // Unbinding: Create the mapping values from an existing User value
      user => Some(user.username, user.email, (user.password, ""), "", user.airlineName)
    }
  )
  
  /**
   * Display an empty form.
   */
  def form = Action { implicit request =>
    Ok(html.signup(signupForm, recaptchaEnabled, recaptchaSiteKey))
  }
  
  /**
   * Display a form pre-filled with an existing User.
   */
//  def editForm = Action {
//    val existingUser = NewUser("fakeuser", "secret", "fake@gmail.com") 
//    Ok(html.signup(signupForm.fill(existingUser)))
//  }

  def airlineNameCheck(airlineName : String)= Action { implicit request =>
    AirlineUtil.checkAirlineName(airlineName) match {
      case Some(rejection) => Ok(Json.obj("rejection" -> rejection))
      case None => Ok(Json.obj("ok" -> true))
    }
  }


   /**
   * Handle form submission.
   */
  def submit = Action { implicit request =>
    signupForm.bindFromRequest().fold(
      // Form has errors, redisplay it
      errors => BadRequest(html.signup(errors, recaptchaEnabled, recaptchaSiteKey)), { userInput =>
        
        if (!recaptchaEnabled || isValidRecaptcha(userInput.recaptchaToken)) {
          // We got a valid User value, display the summary
          val user = User(userInput.username, userInput.email, Calendar.getInstance, Calendar.getInstance, UserStatus.ACTIVE, level = 0, None, List.empty)
          UserSource.saveUser(user)
          Authentication.createUserSecret(userInput.username, userInput.password)
          
          val newAirline = Airline(userInput.airlineName)
//          newAirline.setBalance(50000000) //initial balance 50 million
          newAirline.setMaintenanceQuality(100)
          newAirline.setAirlineCode(newAirline.getDefaultAirlineCode())
          AirlineSource.saveAirlines(List(newAirline))
          UserSource.setUserAirline(user, newAirline)

          SearchUtil.addAirline(newAirline)
          
//          val profile = StartupProfile.profilesById(userInput.profileId)
//          profile.initializeAirline(newAirline)
          Redirect("/").withCookies(Cookie("sessionActive", "true", httpOnly = false)).withSession("userToken" -> SessionUtil.addUserId(user.id))
        } else {
          BadRequest("Recaptcha check failed!")
        }
        //Ok(html.index("User " + user.userName + " created! Please log in"))
      }
    )
  }
  
  def isValidRecaptcha(recaptchaToken: String) : Boolean = {
    println("checking token " + recaptchaToken)
    val request = ws.url(recaptchaUrl).withQueryStringParameters("secret" -> recaptchaSecret, "response" -> recaptchaToken)
    
    val (successJs, scoreJs, actionJs, responseBody) = Await.result(request.get().map { response =>
      ((response.json \ "success"), (response.json \ "score"), (response.json \ "action"), response.body)
    }, Duration(10, TimeUnit.SECONDS))
    
    if (!successJs.as[Boolean]) {
      println("recaptcha response with success as false")
      return false;  
    }
    
    val score = scoreJs.as[Double]
    val action = actionJs.as[String]
    
    println("recaptcha score " + score + " action " + action)
    
    return action == recaptchaAction && score >= recaptchaScoreThreshold
  }
}