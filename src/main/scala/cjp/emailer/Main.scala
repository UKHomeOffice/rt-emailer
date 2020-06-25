package cjp.emailer

import buildinfo.BuildInfo
import com.twitter.finagle.http.filter.JsonpFilter
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, Service}
import com.typesafe.config.ConfigFactory
import grizzled.slf4j.Logging
import io.circe.generic.auto._
import io.finch.circe._
import io.finch.syntax._
import io.finch.{Endpoint, _}
import uk.gov.homeoffice.domain.core.email.EmailRepository
import uk.gov.homeoffice.domain.core.lock.ProcessLockRepository

object Main extends Logging {

  lazy val config = ConfigFactory.load()


  def main(args: Array[String]) = {

    logger.info(s"Config ${config}")

    finagleServer(config.getInt("port"))
    startEmailer()

  }

  val service: Service[Request, Response] = {

    def mongoStatus = {
      try {
        if (EmailMongo.mongoDB.collectionExists("email")) "OK" else "KO"
      } catch {
        case t: Throwable => "KO"
      }
    }

    val ack: Endpoint[Map[String, Boolean]] = get("ack") {
      Ok(Map("ok" -> true))
    }

    val version: Endpoint[Map[String, String]] = get("version") {
      Ok(Map("application" -> "rt-emailer", "version" -> getBuildNumber))
    }

    val status: Endpoint[Map[String, String]] = get("status") {
      Ok(Map("application" -> "rt-emailer", "version" -> getBuildNumber, "MongoDB" -> mongoStatus))
    }


    val endpoint = ack :+: version :+: status

    JsonpFilter.andThen(endpoint.toServiceAs[Application.Json])

  }


  def finagleServer(port: Int) = {
    Http.server.serve(s":${port}", service)
  }

  def startEmailer() = {
    val emailRepository = new EmailRepository with EmailMongo
    val emailSender = new EmailSender(toSmtpConfig)
    val processLockRepository = new ProcessLockRepository with EmailMongo
    val emailer = new Emailer(emailRepository, emailSender, EmailAddress(config.getString("sender"), config.getString("senderName")), Some(EmailAddress(config.getString("replyTo"), config.getString("replyToName"))), config.getInt("pollingFrequency"), processLockRepository)
    emailer.start() // blocks this thread
  }

  def toSmtpConfig = {
    new SmtpConfig(port = config.getInt("smtpServerPort"),
      host = config.getString("smtpServerHost"),
      user = config.getString("smtpServerUsername"),
      password = config.getString("smtpServerPassword"))
  }

  def generateMongoURL() = {
    val dbUser = config.getString("dbUser")
    if (dbUser != "")
      "mongodb://" + dbUser + ":" + config.getString("dbPassword") + "@" + config.getString("dbHost") + "/" + config.getString("dbName") + "?" + config.getString("dbParams")
    else
      "mongodb://" + config.getString("dbHost") + "/" + config.getString("dbName")
  }

  def getBuildNumber: String = {
    import BuildInfo._
    gitTag match {
      case a if a.isEmpty => s"${gitHeadCommit.substring(0, 7)}"
      case _ => gitTag
    }
  }

}
