package uk.gov.homeoffice.rtemailer.model

import org.joda.time.DateTime
import com.typesafe.config.Config
import uk.gov.homeoffice.rtemailer.database.Database

case class AppContext(
  nowF :() => DateTime,
  config :Config,
  database :Database
) {
  def updateAppStatus(updateFunction :AppStatus => AppStatus) :Unit = {
    AppStatus.updateAppStatus(updateFunction)
  }

  def recordMetric(name :String, value :Int) :Unit = {
    // StatsD removed in Scala 3 port.
    // Our alerting system tracks app health via the email table
  }
}

