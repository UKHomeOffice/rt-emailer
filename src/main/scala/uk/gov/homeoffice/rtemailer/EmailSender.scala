package uk.gov.homeoffice.rtemailer

import cats.effect._
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import com.typesafe.scalalogging.StrictLogging

object EmailSender extends StrictLogging {

  def sendMessage(email: Email) :IO[EmailSentResult] = {

    GovNotifyEmailSender.useGovNotify(email) match {
      case true => GovNotifyEmailSender.sendMessage(email)
      case false => SMTPEmailSender.sendMessage(email)
    }
  }

}

