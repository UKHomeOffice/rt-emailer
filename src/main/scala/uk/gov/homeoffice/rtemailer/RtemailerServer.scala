package uk.gov.homeoffice.rtemailer

import cats.effect.IO
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import com.typesafe.scalalogging.StrictLogging
import uk.gov.homeoffice.domain.core.email.{Email, EmailRepository}
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import uk.gov.homeoffice.domain.core.lock.ProcessLockRepository
import cjp.emailer.Emailer
import java.net.InetAddress
import scala.concurrent.duration.Duration
import uk.gov.homeoffice.rtemailer.emailsender._
import uk.gov.homeoffice.rtemailer.model.{AppContext, EmailMongo}
import cats.effect.kernel.Resource

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
    val database = appContext.database

    val processLock = Resource.make(database.obtainLock)(database.releaseLock)
    val emailer = new EmailSender()

    processLock.use { _ =>
      database.getWaitingEmails()
        .evalTap { email :Email => IO.delay(logger.info(s"Sending email to ${email.recipient}")) }
        .evalMap { case email => emailer.sendMessage(email).map { emailSentResult => (email, emailSentResult) } }
        .evalMap { case (email, emailSentResult) => database.updateStatus(email, emailSentResult) }
        .compile
        .toList
        .map { listOfResults =>

          def summerise(emailSentResult :EmailSentResult) :String = emailSentResult match {
            case Sent(_, _) => "SENT"
            case _ => "NOT SENT"
          }

          val countOfResults = listOfResults
            .map(summerise)
            .groupBy(_.toString)
            .mapValues(_.length)
            .toList
            .sortBy(_._1)
            .map { case (k, v) => s"$k = $v"  }.mkString(",")

          if (countOfResults.nonEmpty)
            logger.info(s"Summary: $countOfResults")
          else
            logger.info(s"Summary: There was nothing to do")
        }
    }
  }

}
