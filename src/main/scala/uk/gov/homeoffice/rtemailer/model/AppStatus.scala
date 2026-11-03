package uk.gov.homeoffice.rtemailer.model

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

case class AppStatus(
    appName: String = "",
    version: String = "",
    appStartTime: String = "",

    // status fields
    databaseLastSuccess: Option[DateTime] = None,
    databaseLastError: Option[(DateTime, String)] = None,

    smtpRelayLastSuccess: Option[DateTime] = None,
    smtpRelayLastError: Option[(DateTime, String)] = None,

    govNotifyLastSuccess: Option[DateTime] = None,
    govNotifyLastError: Option[(DateTime, String)] = None,

    emailLastSentSuccess: Option[DateTime] = None,
    emailsSentCount: Long = 0,
    emailsFailedCount: Long = 0
):

  /* if this fails Kubernetes will restart the pod */
  def overallStatus(): Boolean =
    val databaseOk = (databaseLastSuccess, databaseLastError) match
      case (Some(successDt), Some((errorDt, _))) if successDt.isBefore(errorDt) => false
      case _                                                                    => true

    val smtpRelayOk = (smtpRelayLastSuccess, smtpRelayLastError) match
      case (Some(successDt), Some((errorDt, _))) if successDt.isBefore(errorDt) => false
      case _                                                                    => true

    val govNotifyOk = (govNotifyLastSuccess, govNotifyLastError) match
      case (Some(successDt), Some((errorDt, _))) if successDt.isBefore(errorDt) => false
      case _                                                                    => true

    databaseOk && smtpRelayOk && govNotifyOk

  def markDatabaseOk(using appContext: AppContext): AppStatus =
    this.copy(databaseLastSuccess = Some(appContext.nowF()))

  def recordDatabaseError(message: String)(using appContext: AppContext): AppStatus =
    this.copy(databaseLastError = Some((appContext.nowF(), message)))

  def markGovNotifyOk(using appContext: AppContext): AppStatus =
    this.copy(govNotifyLastSuccess = Some(appContext.nowF()))

  def recordGovNotifyError(message: String)(using appContext: AppContext): AppStatus =
    this.copy(govNotifyLastError = Some((appContext.nowF(), message)))

  def recordEmailsSent(success: Int, failed: Int)(using appContext: AppContext): AppStatus =
    val updCounts = this.copy(
      emailsSentCount = emailsSentCount + success,
      emailsFailedCount = emailsFailedCount + failed
    )
    if success > 0 then updCounts.copy(emailLastSentSuccess = Some(appContext.nowF()))
    else updCounts

  def flatten(): Map[String, String] =
    Map(
      "appName" -> appName,
      "version" -> version,
      "appStartTime" -> appStartTime,

      "databaseLastSuccess" -> databaseLastSuccess.map(AppStatus.statusDateTimeFormatter.print).getOrElse(""),
      "databaseLastErrorTime" -> databaseLastError
        .map(e => AppStatus.statusDateTimeFormatter.print(e._1))
        .getOrElse(""),
      "databaseLastErrorMessage" -> databaseLastError.map(_._2).getOrElse(""),

      "smtpRelayLastSuccess" -> smtpRelayLastSuccess.map(AppStatus.statusDateTimeFormatter.print).getOrElse(""),
      "smtpRelayLastErrorTime" -> smtpRelayLastError
        .map(e => AppStatus.statusDateTimeFormatter.print(e._1))
        .getOrElse(""),
      "smtpRelayLastErrorMessage" -> smtpRelayLastError.map(_._2).getOrElse(""),

      "govNotifyLastSuccess" -> govNotifyLastSuccess.map(AppStatus.statusDateTimeFormatter.print).getOrElse(""),
      "govNotifyLastErrorTime" -> govNotifyLastError
        .map(e => AppStatus.statusDateTimeFormatter.print(e._1))
        .getOrElse(""),
      "govNotifyLastErrorMessage" -> govNotifyLastError.map(_._2).getOrElse(""),

      "emailsLastSentSuccess" -> emailLastSentSuccess.map(AppStatus.statusDateTimeFormatter.print).getOrElse(""),
      "emailsSentCount" -> emailsSentCount.toString,
      "emailsFailedCount" -> emailsFailedCount.toString
    )

// A single global used to capture the shared health of the system.
object AppStatus:
  private val statusDateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")
  private var globalAppStatus = AppStatus()
  def updateAppStatus(updateFunction: AppStatus => AppStatus): Unit = globalAppStatus = updateFunction(globalAppStatus)
  def getAppStatus: AppStatus = globalAppStatus
