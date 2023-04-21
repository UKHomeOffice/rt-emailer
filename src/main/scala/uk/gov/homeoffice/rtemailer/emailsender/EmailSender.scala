package uk.gov.homeoffice.rtemailer

import cats.effect._
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import com.typesafe.scalalogging.StrictLogging
import uk.gov.homeoffice.rtemailer.model.AppContext

class EmailSender(implicit appContext :AppContext) extends StrictLogging {

  def sendMessage(email: Email) :IO[EmailSentResult] = {

    val govNotifyEmailSender = new GovNotifyEmailSender()

    govNotifyEmailSender.useGovNotify(email).flatMap {
      case Left(govNotifyError) =>
        // I do not (think I) want to fallback from GovNotify
        // to SMTP if GovNotify is down temporarily but rather wait
        // for system to recover. Using the SMTP method is only when
        // gov notify hasn't got a matching template. (After the migration
        // of 90+ emails, SMTP code path can be deprecated).
        logger.error(s"GovNotify.useGovNotify returned an error. Waiting for it to recover: $govNotifyError")
        IO.delay(Waiting)
      case Right(true) => govNotifyEmailSender.sendMessage(email)
      case Right(false) => new SMTPEmailSender().sendMessage(email)
    }
  }

}

