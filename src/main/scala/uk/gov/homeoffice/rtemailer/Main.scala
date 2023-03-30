package uk.gov.homeoffice.rtemailer

import cats.effect.IOApp
import buildinfo.BuildInfo
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import scala.util.Try
import scala.io.Source
import java.time.ZonedDateTime
import uk.gov.homeoffice.mongo.casbah.Mongo
import com.mongodb.casbah.MongoClientURI
import scala.concurrent.duration._

object Main extends IOApp.Simple {
  var run = RtemailerServer.run 
}

case class AppStatus(
  appName :String,
  version :String,
  appEnabled :Boolean,
  dbConnectionOk :Boolean,
  emailConnectionOk :Boolean,
  emailsSent :Long,
  emailsFailedToSend :Long,
  appStartTime :String
)

object Globals extends StrictLogging {

  val isJar = Try(Source.fromFile("src/main/resources/logback.xml")).isFailure

  val config :Config = {
    val config = ConfigFactory.load()
    logger.info("Configuration Loaded")
    if (isJar) {
      logger.info("isJar: true. resource files streamed from jar")
    } else {
      logger.info("isJar: false. resource files reloaded from disk on each call")
    }
    config
  }

  val dbHost = config.getString("db.host")
  val dbName = config.getString("db.name")
  val dbUser = config.getString("db.user")
  val dbPassword = config.getString("db.password")
  val dbParams = config.getString("db.params")
  val mongoConnectionString = dbUser.isEmpty match {
    case false =>
      logger.info(s"mongo connection string: mongodb://$dbUser:*******@$dbHost/$dbName?$dbParams")
      s"mongodb://$dbUser:$dbPassword@$dbHost/$dbName?$dbParams"
    case true =>
      val cs = s"mongodb://$dbHost/$dbName"
      logger.info(s"mongo connection string: $cs")
      cs
  }

  val mongoDB = Mongo.mongoDB(MongoClientURI(mongoConnectionString))

  var status = AppStatus(
    appName = "rt-emailer",
    version = BuildInfo.version,
    appEnabled = config.getBoolean("app.enabled"),
    dbConnectionOk = false,
    emailConnectionOk = false,
    emailsSent = 0,
    emailsFailedToSend = 0,
    appStartTime = ZonedDateTime.now.toString()
  )

  def recordEmailsSent(sent :Int, failedToSend :Int) :Unit =
    status = status.copy(
      emailsSent = status.emailsSent + sent,
      emailsFailedToSend = status.emailsFailedToSend + failedToSend
    )

  def setDBConnectionOk(isOk: Boolean) :Unit =
    status = status.copy(dbConnectionOk = isOk)

  def setEmailConnectionOk(isOk: Boolean) :Unit =
    status = status.copy(emailConnectionOk = isOk)

  val emailPollingFrequency :Duration = Duration(Globals.config.getString("app.emailPollingFrequency"))

  logger.info(s"rt-emailer application started")
  logger.info(s"rt-emailer version ${status.version}, started at ${status.appStartTime}. email sending enabled: ${status.appEnabled}. polling frequency: $emailPollingFrequency")

}

import uk.gov.homeoffice.mongo.casbah.Mongo

trait EmailMongo extends Mongo {
  lazy val mongoDB = Globals.mongoDB
}

