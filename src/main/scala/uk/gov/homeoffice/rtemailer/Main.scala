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
import github.gphat.censorinus._
import github.gphat.censorinus.statsd.Encoder

object Main extends IOApp.Simple with StrictLogging {

  // 1. load config
  // 2. connect to database
  // 3. set up a mechanism to track app status
  // 4. call http4s run

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

  val database = Database.make(config)
  logger.info(s"Database: ${database.name}")

  val statsDClient = new Client(
    sender = new UDPSender(
      hostname = config.getString("statsd.host"),
      port = config.getInt("statsd.port"),
      allowExceptions = false
    ),
    encoder = Encoder,
    prefix = config.getString("statsd.prefix")
  ) {
    override def enqueue(metric: Metric, sampleRate: Double, bypassSampler: Boolean): Unit = {
      val prefixedMetric = metric match {
        case c: CounterMetric => c.copy(name=makeName(c.name))
        case g: GaugeMetric => g.copy(name=makeName(g.name))
        case h: HistogramMetric => h.copy(name=makeName(h.name))
        case s: SetMetric => s.copy(name=makeName(s.name))
        case ms: TimerMetric => ms.copy(name=makeName(ms.name))
        case m :MeterMetric => m.copy(name=makeName(m.name))
        case e => e
      }
      super.enqueue(prefixedMetric, sampleRate, bypassSampler)
    }
  }

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
    statsDClient
  )

  var run = RtemailerServer.run(appContext)
}

