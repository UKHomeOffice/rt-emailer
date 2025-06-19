package uk.gov.homeoffice.rtemailer

import cats.effect.IOApp
import buildinfo.BuildInfo
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import scala.util.Try
import scala.io.Source
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import scala.concurrent.duration._
import uk.gov.homeoffice.rtemailer.model.{AppStatus, AppContext}
import uk.gov.homeoffice.rtemailer.database._

object Main extends IOApp.Simple with StrictLogging {

  // 1. load config
  // 2. connect to database
  // 3. set up a mechanism to track app status
  // 4. call http4s run

  val isJar = Try(Source.fromFile("src/main/resources/logback.xml")).isFailure

  val config :Config = {
    val config = ConfigFactory.load()
    logger.info("Configuration Loaded")
    if isJar then {
      logger.info("isJar: true. resource files streamed from jar")
    } else {
      logger.info("isJar: false. resource files reloaded from disk on each call")
    }
    config
  }

  val database = Database.make(config)
  logger.info(s"Database: ${database.name}")

  // set up a global variable called appStatus that can be manipulated
  // to show stats on the /status endpoint.
  val appStatus = AppStatus(
    appName = "rt-emailer",
    version = BuildInfo.version,
    appStartTime = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ").print(DateTime.now)
  )
  AppStatus.updateAppStatus(_ => appStatus)

  val emailPollingFrequency :Duration = Duration(config.getString("app.emailPollingFrequency"))

  logger.info(s"rt-emailer application started")
  logger.info(s"rt-emailer version ${appStatus.version}, started at ${appStatus.appStartTime}. polling frequency: $emailPollingFrequency")

  var appContext = AppContext(
    DateTime.now,
    config,
    database,
  )

  var run = RtemailerServer.run(appContext)
}

