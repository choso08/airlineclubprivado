package controllers

import java.time.LocalDateTime
import java.util.UUID

object SessionUtil {
  def getUserId(token : String) = {
    Session.extractUserId(token)
  }

  def addUserId(userId : Int) = {
    Session.generateToken(userId)
  }
}

case class Session(token: String, userId : Int, var expiration: LocalDateTime)

/**
 * Login tokens.
 *
 * These used to live in a plain in-memory Map, which meant every restart of
 * the web front-end logged everyone out. With automatic updates restarting it
 * whenever a change lands, players were being thrown back to the login screen
 * regularly and for no reason they could see.
 *
 * The token is now self-describing and signed rather than looked up:
 *
 *     <userId>.<expiryEpochSeconds>.<hmac of the two, with the app secret>
 *
 * Nothing is stored, so nothing is lost on restart, and it stays as
 * unforgeable as the in-memory version: without AIRLINE_APP_SECRET you cannot
 * produce a valid signature for a user id you were not given. (Play's own
 * session cookie is signed with the same secret, so this is a second lock on
 * a door that already has one.)
 *
 * The 24 hour expiry is kept. It no longer slides on each request - a token is
 * good for 24 hours from when it was issued - because refreshing it would mean
 * writing state again, which is the thing being removed. Logging in again once
 * a day is a fair trade for not being logged out by every update.
 */
object Session {
  private[this] val EXPIRY_HOURS = 24

  private[this] lazy val secret : String = {
    val config = com.typesafe.config.ConfigFactory.load()
    val configured =
      if (config.hasPath("play.http.secret.key")) config.getString("play.http.secret.key") else ""
    if (configured.isEmpty || configured == "changeme") {
      // Refuse to sign with a known value: anyone could then mint a token for
      // any account. Fall back to a per-process random secret, which behaves
      // like the old in-memory map (everyone is logged out on restart) rather
      // than being insecure.
      println("WARNING: AIRLINE_APP_SECRET is unset or default - login sessions will not survive a restart.")
      UUID.randomUUID().toString
    } else {
      configured
    }
  }

  private[this] def sign(payload : String) : String = {
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"))
    java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(mac.doFinal(payload.getBytes("UTF-8")))
  }

  def generateToken(userId : Int): String = {
    val expiry = java.time.Instant.now().plusSeconds(EXPIRY_HOURS * 3600L).getEpochSecond
    val payload = s"$userId.$expiry"
    s"$payload.${sign(payload)}"
  }

  def extractUserId(token : String) : Option[Int] = {
    token.split('.') match {
      case Array(userIdPart, expiryPart, signature) =>
        val payload = s"$userIdPart.$expiryPart"
        // Constant-time compare, so the signature cannot be guessed a byte at
        // a time by timing the responses.
        val expected = sign(payload)
        val matches = java.security.MessageDigest.isEqual(
          expected.getBytes("UTF-8"), signature.getBytes("UTF-8"))

        if (!matches) {
          None
        } else {
          for {
            userId <- userIdPart.toIntOption
            expiry <- expiryPart.toLongOption
            if expiry > java.time.Instant.now().getEpochSecond
          } yield userId
        }

      case _ => None   // not one of ours, or an old-format token from before this change
    }
  }
}
