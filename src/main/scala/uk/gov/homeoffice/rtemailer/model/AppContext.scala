package uk.gov.homeoffice.rtemailer.model

import com.typesafe.config.Config
import org.joda.time.DateTime
import uk.gov.homeoffice.rtemailer.database.Database

case class AppContext(
    nowF: () => DateTime,
    config: Config,
    database: Database
):
  def updateAppStatus(updateFunction: AppStatus => AppStatus): Unit =
    AppStatus.updateAppStatus(updateFunction)
