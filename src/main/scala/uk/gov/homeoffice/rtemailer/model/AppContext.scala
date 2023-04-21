package uk.gov.homeoffice.rtemailer.model

import org.joda.time.DateTime
import com.typesafe.config.Config
import com.mongodb.casbah.MongoDB

case class AppContext(
  nowF :() => DateTime,
  config :Config,
  mongoDB :MongoDB
) {
  def updateAppStatus(updateFunction :AppStatus => AppStatus) :Unit = {
    AppStatus.updateAppStatus(updateFunction)
  }
}

