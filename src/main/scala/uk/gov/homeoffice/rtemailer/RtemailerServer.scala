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

object RtemailerServer extends StrictLogging {

  def run :IO[Unit] = {
    val httpApp = (RtemailerRoutes.allRoutes[IO]()).orNotFound

    lazy val emailerLoop: IO[Unit] = {
      sendEmails >>
      IO.sleep(Globals.emailPollingFrequency) >>
      emailerLoop
    }

    EmberServerBuilder.default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(Port.fromInt(Globals.config.getInt("app.webPort")).get)
      .withHttpApp(httpApp)
      .build
      .use(server =>
        IO.delay(logger.info(s"Server has started: ${server.address}")) >>
        emailerLoop >>
        IO.never
      )
  }

  def sendEmails() :IO[Unit] = {

    val processLockRepository = new ProcessLockRepository with EmailMongo
    IO.blocking(processLockRepository.obtainLock("rt-emailer", InetAddress.getLocalHost.getHostName)).flatMap {
      case Some(lock) =>
        logger.info(s"Lock aquired. Ready to send emails")
        val emailRepository = new EmailRepository with EmailMongo
        val emailer = new Emailer(emailRepository, EmailSender.sendMessage)
        Globals.setDBConnectionOk(true)
        emailer.sendEmails().flatMap { emailResults =>
          processLockRepository.releaseLock(lock)
          emailResults match {
            case Right(emailListWithStatus) =>
              logger.info(s"Attempted to send ${emailListWithStatus.length} emails")
              emailListWithStatus.zipWithIndex.foreach { case ((email, newStatus), idx) =>
                logger.info(s"Email $idx: to=${email.recipient}, subject=${email.subject}, caseRef=${email.caseRef}, newStatus=${newStatus}")
              }
              Globals.recordEmailsSent(emailListWithStatus.count(_._2 == Sent), emailListWithStatus.count(_._2 != Sent))
              IO.delay(logger.info(s"Finished sending emails. System is configured to sleep for ${Globals.emailPollingFrequency}"))
            case Left(error) =>
              Globals.setDBConnectionOk(false)
              IO.delay(logger.error(s"Error. System unhealthy: $error"))
          }
        }
      case None =>
        IO.delay(logger.info(s"Lock not available. Doing nothing"))
    }
  }

}
