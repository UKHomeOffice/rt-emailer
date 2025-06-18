package uk.gov.homeoffice.rtemailer

import cats.effect.IO
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import com.typesafe.scalalogging.StrictLogging
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import scala.concurrent.duration.Duration
import uk.gov.homeoffice.rtemailer.emailsender._
import uk.gov.homeoffice.rtemailer.model.AppContext
import cats.effect.kernel.Resource

object RtemailerServer extends StrictLogging {

  def run(implicit appContext :AppContext) :IO[Unit] = {
    val httpApp = (RtemailerRoutes.allRoutes[IO]()).orNotFound

    val emailPollingFrequency :Duration = Duration(appContext.config.getString("app.emailPollingFrequency"))

    lazy val emailerLoop: IO[Unit] = {
      sendEmails() >>
      IO.sleep(emailPollingFrequency) >>
      emailerLoop
    }

    EmberServerBuilder.default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(Port.fromInt(appContext.config.getInt("app.webPort")).get)
      .withHttpApp(httpApp)
      .build
      .use(server =>
        IO.delay(logger.info(s"Server has started: ${server.address}")) >>
        emailerLoop >>
        IO.never
      )
  }

  def sendEmails()(implicit appContext :AppContext) :IO[Unit] = {
    val database = appContext.database

    val processLock = Resource.make(database.obtainLock())(database.releaseLock)
    val emailer = new EmailSender()

    processLock.use { _ =>
      database.getWaitingEmails()
        .evalTap { (email :Email) => IO.delay(logger.info(s"Sending email to ${email.recipient}")) }
        .evalMap { case email => emailer.sendMessage(email).map { emailSentResult => (email, emailSentResult) } }
        .evalMap { case (email, emailSentResult) => database.updateStatus(email, emailSentResult) }
        .compile
        .toList
        .map { listOfResults =>

          val sentCount = listOfResults.collect { case Sent(_, _) => 1 }.sum
          val unsentCount = listOfResults.length - sentCount

          if listOfResults.nonEmpty then
            logger.info(s"Summary: SENT = $sentCount, NOT SENT = $unsentCount")
          else
            logger.info(s"Summary: There was nothing to do")
          appContext.updateAppStatus(_.recordEmailsSent(sentCount, unsentCount))
        }
    }
  }

}
