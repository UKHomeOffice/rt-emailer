package uk.gov.homeoffice.rtemailer

import cats.effect._
import cats.data.NonEmptyList
import emil._
import emil.builder._
import emil.javamail._
import emil.javamail.syntax._
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import cats.effect.unsafe.implicits.global
import com.typesafe.scalalogging.StrictLogging

object EmailSender extends StrictLogging {

  implicit class StringOps(val underlying :String) extends AnyVal {
    def asMailAddress :MailAddress = {
      MailAddress.parse(underlying) match {
        case Right(em) => em
        case Left(err) => throw new Exception(s"Invalid email address: $underlying ($err)")
      }
    }
  }

  private def buildMessage(email: Email) :Mail[IO] = {

    val mailBuilder = MailBuilder[IO]()
    .add(From(Globals.config.getString("smtp.sender").asMailAddress)) // TODO: senderName for sender Name
    .add(To(email.recipient.asMailAddress))
    .set(CustomHeader(Header("reply-to", Globals.config.getString("smtp.replyTo"))))
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
    s"${Globals.config.getString("smtp.protocol")}://${Globals.config.getString("smtp.host")}:${Globals.config.getString("smtp.port")}",
    Globals.config.getString("smtp.username"),
    Globals.config.getString("smtp.password"),
    SSLType.NoEncryption /* todo: support */
  )

  def sendMessage(email: Email) :EmailSentResult = {

    val emailToSend :Mail[IO] = buildMessage(email)
    val sendIO: IO[NonEmptyList[String]] = senderImpl(smtpConf).send(emailToSend)
    val result = sendIO.unsafeRunSync()
    logger.info(s"Email reciept: ${result.toList.mkString(",")}")
    Sent
  }
}

