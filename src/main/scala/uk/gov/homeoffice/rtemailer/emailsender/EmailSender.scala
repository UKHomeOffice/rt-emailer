package uk.gov.homeoffice.rtemailer.emailsender

import cats.effect._
import com.typesafe.scalalogging.StrictLogging
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import uk.gov.homeoffice.rtemailer.model.AppContext

class EmailSender(using appContext: AppContext) extends StrictLogging:
  private val govNotifyEmailSender = new GovNotifyEmailSender

  def sendMessage(email: Email): IO[EmailSentResult] =
    govNotifyEmailSender.sendMessage(email)
