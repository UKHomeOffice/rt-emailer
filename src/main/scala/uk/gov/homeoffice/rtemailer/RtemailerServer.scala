package uk.gov.homeoffice.rtemailer

import cats.effect.IO
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import com.typesafe.scalalogging.StrictLogging
import uk.gov.homeoffice.domain.core.email.EmailRepository
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import uk.gov.homeoffice.domain.core.lock.ProcessLockRepository
import cjp.emailer.Emailer
import java.net.InetAddress
import scala.concurrent.duration.Duration
import uk.gov.homeoffice.rtemailer.model.{AppContext, EmailMongo}

object RtemailerServer extends StrictLogging {

  def run(implicit appContext :AppContext) :IO[Unit] = {
    val httpApp = (RtemailerRoutes.allRoutes[IO]()).orNotFound

    val emailPollingFrequency :Duration = Duration(appContext.config.getString("app.emailPollingFrequency"))

    lazy val emailerLoop: IO[Unit] = {
      sendEmails >>
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

    val processLockRepository = new ProcessLockRepository with EmailMongo
    IO.blocking(processLockRepository.obtainLock("rt-emailer", InetAddress.getLocalHost.getHostName)).flatMap {
      case Some(lock) =>
        logger.info(s"Lock aquired. Ready to send emails")
        val emailRepository = new EmailRepository with EmailMongo
        val emailSender = new EmailSender
        val emailer = new Emailer(emailRepository, emailSender.sendMessage)
        appContext.updateAppStatus(_.markDatabaseOk)
        emailer.sendEmails().flatMap { emailResults =>
          processLockRepository.releaseLock(lock)
          emailResults match {
            case Right(emailListWithStatus) =>
              logger.info(s"Attempted to send ${emailListWithStatus.length} emails")
              emailListWithStatus.zipWithIndex.foreach {
                case ((email, Sent(_, _)), idx) =>
                  logger.info(s"Email $idx: to=${email.recipient}, subject=${email.subject}, caseRef=${email.caseRef}, newStatus=Sent")
                case ((email, newStatus), idx) =>
                  logger.info(s"Email $idx: to=${email.recipient}, subject=${email.subject}, caseRef=${email.caseRef}, newStatus=${newStatus}")
              }
              val (emailsSent, emailsNotSent) = emailListWithStatus.map(_._2).partition { case Sent(_, _) => true; case _ => false }
              appContext.updateAppStatus(_.recordEmailsSent(emailsSent.length, emailsNotSent.length))
              IO.delay(logger.info(s"Finished sending emails"))
            case Left(error) =>
              appContext.updateAppStatus(_.recordDatabaseError(error))
              IO.delay(logger.error(s"Error. System unhealthy: $error"))
          }
        }
      case None =>
        /* This isn't an error but it may be useful to shout loudly about why things are working */
        appContext.updateAppStatus(_.recordDatabaseError("lock not available"))
        IO.delay(logger.info(s"Lock not available"))
    }
  }

}
