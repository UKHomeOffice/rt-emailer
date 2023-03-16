package uk.gov.homeoffice.rtemailer

import cats.effect.IO
import com.comcast.ip4s._
import scala.concurrent.duration._
import org.http4s.ember.server.EmberServerBuilder
import com.typesafe.scalalogging.StrictLogging
import uk.gov.homeoffice.domain.core.email.EmailRepository
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

  def sendEmails() :IO[Unit] = IO.delay {
    val processLockRepository = new ProcessLockRepository with EmailMongo
    val emailRepository = new EmailRepository with EmailMongo

    processLockRepository.obtainLock("rt-emailer", InetAddress.getLocalHost.getHostName) match {
      case Some(lock) =>
        logger.info(s"Lock aquired. Ready to send emails")
        val emailer = new Emailer(emailRepository, EmailSender.sendMessage)
        val emailResults = emailer.sendEmails()
        processLockRepository.releaseLock(lock)
        emailResults match {
          case Right(emailListWithStatus) =>
            logger.info(s"Attempted to send ${emailListWithStatus.length} emails")
            emailListWithStatus.zipWithIndex.foreach { case ((email, newStatus), idx) =>
              logger.info(s"Email $idx: to=${email.recipient}, subject=${email.subject}, caseRef=${email.caseRef}, newStatus=${newStatus}")
            }
            logger.info(s"Finished sending emails. System is configured to sleep for ${Globals.emailPollingFrequency}")
          case Left(error) =>
            logger.error(s"Error. System unhealthy. Sleeping for 3 minutes then killing service: $error")
            IO.sleep(Duration("180 seconds"))
            sys.exit()
        }
      case None =>
        logger.info(s"Lock not available. Doing nothing")
    }
  }
}
