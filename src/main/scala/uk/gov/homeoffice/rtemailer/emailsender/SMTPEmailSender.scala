package uk.gov.homeoffice.rtemailer.emailsender

import cats.effect._
import emil._
import emil.builder._
import emil.javamail._
import emil.javamail.syntax._
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import com.typesafe.scalalogging.StrictLogging
import uk.gov.homeoffice.rtemailer.model.AppContext

class SMTPEmailSender(implicit appContext :AppContext) extends StrictLogging {
  import SMTPEmailSender._

  private def buildMessage(email: Email) :Mail[IO] = {

    val mailBuilder = MailBuilder[IO]()
    .add(From(appContext.config.getString("smtp.sender").asMailAddress)) // TODO: senderName for sender Name
    .add(To(email.recipient.asMailAddress))
    .set(CustomHeader(Header("reply-to", appContext.config.getString("smtp.replyTo"))))
    .set(Subject(email.subject))
    .set(TextBody(email.text))
    .set(HtmlBody(email.html))

    val builderWithCC = email.cc.foldLeft(mailBuilder) { case (builder, nextCC) =>
      builder.add(Cc(nextCC.asMailAddress))
    }

    builderWithCC.build
  }

  val senderImpl = JavaMailEmil[IO]()

  val smtpConf = MailConfig(
    s"${appContext.config.getString("smtp.protocol")}://${appContext.config.getString("smtp.host")}:${appContext.config.getString("smtp.port")}",
    appContext.config.getString("smtp.username"),
    appContext.config.getString("smtp.password"),
    SSLType.NoEncryption
  )

  def sendMessage(email: Email) :IO[EmailSentResult] = {

    val emailToSend :Mail[IO] = buildMessage(email)
    senderImpl(smtpConf).send(emailToSend).map { result =>
      logger.info(s"Email reciept: ${result.toList.mkString(",")}")
      appContext.updateAppStatus(_.markDatabaseOk)
      appContext.updateAppStatus(_.markSmtpRelayOk)
      Sent()
    }.handleErrorWith { exc =>
      logger.error(s"Exception thrown trying to send email: $exc")
      appContext.updateAppStatus(_.markSmtpRelayOk)
      IO.delay(TransientError(exc.getMessage))
    }
  }
}

object SMTPEmailSender {

  implicit class StringOps(val underlying :String) extends AnyVal {
    def asMailAddress :MailAddress = {
      MailAddress.parse(underlying) match {
        case Right(em) => em
        case Left(err) => throw new Exception(s"Invalid email address: $underlying ($err)")
      }
    }
  }

}
