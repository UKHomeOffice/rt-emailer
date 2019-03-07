package cjp.emailer

import cjp.emailer.Config._
import com.twitter.finagle.http.filter.JsonpFilter
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, Service}
import com.typesafe.config.{Config, ConfigFactory}
import io.finch.Endpoint
import grizzled.slf4j.Logging
import io.finch._
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.{Loader, Yaml}
import uk.gov.homeoffice.domain.core.email.EmailRepository
import uk.gov.homeoffice.domain.core.lock.ProcessLockRepository

import scala.io.BufferedSource
import scala.io.Source._
import scala.util.Try
import io.finch.syntax._
import io.circe.generic.auto._
import io.finch.circe._
import buildinfo.BuildInfo

object Main extends Logging {
  def main(args: Array[String]) = {

    val isKube = Try(System.getProperty("kube").toBoolean) toOption

    logger.info(s"Value for kube : $isKube")

    isKube match {
      case Some(a) if a == true =>
        configFromEnv(ConfigFactory.load())

        logger.info(s"Config ${Config.config}")

        finagleServer(Config.config.port)
        startEmailer(Config.config)

      case None => //TODO Following implementation needs to be removed once this application is not running in old platform
        parser.parse(args, CommandLineArguments()) map { commandLineArguments =>
          processCommandLine(commandLineArguments)
        } getOrElse {
          // arguments are bad, error message will have been displayed
          logger.info(s"Invalid arguments")
        }
    }
  }

  lazy val parser = new scopt.OptionParser[CommandLineArguments]("iris") {
    head("iris", "")

    opt[String]('c', "config") required() action { (x, c) =>
      c.copy(config = x)
    } text "Required - this is the absolute filepath of the config file"
    help("help") text "prints this usage text"
  }

  def processCommandLine(commandLineArguments: CommandLineArguments) {
    logger.info("Config file path = " + commandLineArguments.config)


    val sourceOption: Option[BufferedSource] = Try(scala.io.Source.fromFile(commandLineArguments.config)) toOption

    sourceOption match {
      case Some(source) =>
        val config = readFromConfig(source)
        Config.config = config
        finagleServer(Config.config.port)
        startEmailer(Config.config)
      case None => logger.info("Config file does not exist")
    }
  }

  def finagleServer(port: Int) = {
    Http.server.serve(s":${port}", service)
  }

  def startEmailer(config: Configuration) = {
    val emailRepository = new EmailRepository with EmailMongo
    val emailSender = new EmailSender(config.toSmtpConfig)
    val processLockRepository = new ProcessLockRepository with EmailMongo
    val emailer = new Emailer(emailRepository, emailSender, EmailAddress(config.sender, config.senderName), Some(EmailAddress(config.replyTo, config.replyToName)), config.pollingFrequency, processLockRepository)
    emailer.start() // blocks this thread
  }

  def configFromEnv(con: Config) = {
    config = new Configuration()
    config.setDbHost(con.getString("dbHost"))
    config.setDbName(con.getString("dbName"))
    config.setDbUser(con.getString("dbUser"))
    config.setDbPassword(con.getString("dbPassword"))
    config.setPort(con.getInt("port"))
    config.setSender(con.getString("sender"))
    config.setSenderName(con.getString("senderName"))
    config.setReplyTo(con.getString("replyTo"))
    config.setReplyToName(con.getString("replyToName"))
    config.setPollingFrequency(con.getInt("pollingFrequency"))

    config.setSmtpServerHost(con.getString("smtpServerHost"))
    config.setSmtpServerPort(con.getInt("smtpServerPort"))
    config.setSmtpServerUsername(con.getString("smtpServerUsername"))
    config.setSmtpServerPassword(con.getString("smtpServerPassword"))
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

  def readFromConfig(source: BufferedSource) = {
    val configFile = source.mkString
    source.close()
    val yaml = new Yaml(new Loader(new Constructor(classOf[Configuration])))
    val config = yaml.load(configFile).asInstanceOf[Configuration]
    config
  }
}

object Config {
  var config: Configuration = _

  val isKube = Try(System.getProperty("kube").toBoolean).getOrElse(false)

  def getBuildNumber = {

    if (isKube) {
      getBuildNumberFromGit
    } else {
      getBuildNumberFromConf
    }
  }

  def getBuildNumberFromGit: String = {
    import BuildInfo._
    gitTag match {
      case a if a.isEmpty => s"${gitHeadCommit.substring(0, 7)}"
      case _ => gitTag
    }
  }

  def getBuildNumberFromConf = fromInputStream(getClass.getResourceAsStream("/version.conf"))
    .mkString.stripLineEnd
    .replaceAll("\"", "")
    .replaceAll("buildNumber=", "")
}
