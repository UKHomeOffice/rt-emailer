package uk.gov.homeoffice.rtemailer.model

import org.joda.time.DateTime
import com.typesafe.config.Config
import uk.gov.homeoffice.rtemailer.database.Database
//import github.gphat.censorinus._

case class AppContext(
  nowF :() => DateTime,
  config :Config,
  database :Database //,
  //statsDClient :Client
) {
  def updateAppStatus(updateFunction :AppStatus => AppStatus) :Unit = {
    AppStatus.updateAppStatus(updateFunction)
  }

  def recordMetric(name :String, value :Int) :Unit = {
    //Option(statsDClient).foreach(_.enqueue(GaugeMetric(name, value.toDouble)))
  }
}

