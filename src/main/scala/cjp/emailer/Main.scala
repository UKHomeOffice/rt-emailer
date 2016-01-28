package cjp.emailer

import java.net.InetSocketAddress

import caseworkerdomain.lock.ProcessLockRepository
import domain.core.email.EmailRepository
import cjp.emailer.Config._
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.httpx.path._
import com.twitter.finagle.httpx.{Http, Method}
import grizzled.slf4j.Logging
import io.finch._
import io.finch.json._
import io.finch.json.finch._
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.{Loader, Yaml}

import scala.io.BufferedSource
import scala.io.Source._
import scala.util.Try

object Main extends Logging {
  def main(args: Array[String]) = {
    parser.parse(args, CommandLineArguments()) map { commandLineArguments =>
      processCommandLine(commandLineArguments)
    } getOrElse {
      // arguments are bad, error message will have been displayed
    }
  }

  val parser = new scopt.OptionParser[CommandLineArguments]("iris") {
    head("iris", "")

    opt[String]('c', "config") required() action { (x, c) =>
      c.copy(config = x)
    } text "Required - this is the absolute filepath of the config file"
    help("help") text "prints this usage text"
  }

  def processCommandLine(commandLineArguments: CommandLineArguments) {
    logger.info("Config file path = " + commandLineArguments.config)
    val sourceOption = Try(scala.io.Source.fromFile(commandLineArguments.config)) toOption

    sourceOption match {
      case Some(source) =>
        val config = readFromConfig(source)
        Config.config = config

        exposeEndpoints(Config.config.port)

        val emailRepository = new EmailRepository(config.getMongoDBConnector)
        val emailSender = new EmailSender(config.toSmtpConfig)
        val processLockRepository = new ProcessLockRepository(config.getMongoDBConnector)
        val emailer = new Emailer(emailRepository, emailSender, EmailAddress(config.sender, config.senderName), config.pollingFrequency, processLockRepository)
        emailer.start() // blocks this thread

      case None => println("Config file does not exist")
    }
  }

  def exposeEndpoints(port: Int) = {
    val endpoint = Endpoints ! TurnModelIntoJson ! TurnJsonIntoHttp[Json]
    val backend = endpoint orElse Endpoint.NotFound

    ServerBuilder()
      .codec(Http())
      .bindTo(new InetSocketAddress(port))
      .name("monitoring-and-version")
      .build(endpoint.toService)
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

  def getBuildNumber = fromInputStream(getClass.getResourceAsStream("/version.conf"))
    .mkString.stripLineEnd
    .replaceAll("\"", "")
    .replaceAll("buildNumber=", "")
}

case class Ack() extends JsonResponse {
  override def toJson: Json = Json.obj("ok" -> true)
}

case class GetAck() extends Service[HttpRequest, JsonResponse] {
  def apply(req: HttpRequest) = {
    Ack.apply().toFuture
  }
}

case class AppVersion() extends JsonResponse {
  override def toJson: Json = Json.obj("application" -> "rt-emailer", "version" -> getBuildNumber)
}

case class GetVersion() extends Service[HttpRequest, JsonResponse] {
  def apply(req: HttpRequest) = AppVersion.apply().toFuture
}

trait JsonResponse {
  def toJson: Json
}

object Endpoints extends Endpoint[HttpRequest, JsonResponse] {
  def route = {
    case Method.Get -> Root / "ack" => GetAck()
    case Method.Get -> Root / "version" => GetVersion()
  }
}

case class EmailAddress(email: String, name: String)

object TurnModelIntoJson extends Service[JsonResponse, Json] {
  def apply(model: JsonResponse) = model.toJson.toFuture
}