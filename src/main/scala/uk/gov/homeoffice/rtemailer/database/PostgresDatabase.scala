package uk.gov.homeoffice.rtemailer.database

import uk.gov.homeoffice.rtemailer.model._
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import uk.gov.homeoffice.rtemailer.model._
import uk.gov.homeoffice.mongo.casbah.{MongoDBObject, MongoDBList}
import com.mongodb.DBObject
import org.bson.types.ObjectId

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime
import uk.gov.homeoffice.domain.core.lock._

import io.circe._
import cats.effect.kernel._

import skunk._
import skunk.implicits._
import skunk.codec.all._
import skunk.circe.codec.json.jsonb

import natchez.Trace.Implicits.noop
import cats.effect.std.Console

import com.typesafe.config.Config

class PostgresDatabase(config :Config) extends Database with StrictLogging {
  implicit val consoleForIO: Console[IO] = Console.make[IO]

  val name = "Postgres Database"

  // These are effectively no-ops for Postgres as we plan to rely on kubernetes
  // enforcing only a single-instance running at a given time, rather than reimplementing
  // the old locking mechanism that older systems use.

  def obtainLock() :IO[Lock] = { IO.delay(new Lock(new ObjectId(), "rt-emailer", "server", DateTime.now)) }
  def releaseLock(lock :Lock) :IO[Unit] = IO.delay(())

  def makeSession() :Resource[IO, Session[IO]] = {
    def strToOption(in :String) = if in.isEmpty then None else Some(in)

    Session.single(
      host     = config.getString("db.postgres.host"),
      port     = config.getInt("db.postgres.port"),
      user     = config.getString("db.postgres.user"),
      database = config.getString("db.postgres.database"),
      password = strToOption(config.getString("db.postgres.password"))
    )
  }

  /* When porting from Mongo to Postgres the aim was to remove all Mongo dependencies. However due to time
   * pressures, I haven't got time to rewrite all the personalisation logic into a netural JSON format. As a
   * tactical and quick implementation, the Postgres library returns a Mongo object. This is known piece of
   * tech debt and I have created DPSPS-1419 to come back and do this work correctly.
   */
  def jsonToMongo(json :Json) :MongoDBObject = {
    val builder = MongoDBObject.newBuilder()

    val jsonFields :List[(String, Json)] = json.asObject.map(_.toList).getOrElse(List.empty)
    jsonFields.foreach { case (key, jsonValue) => jsonValue match {
      case j if j.isNumber => j.as[Int].toOption.foreach { intValue => builder += (key -> intValue) }
      case j if j.isArray =>
        j.asArray.foreach { (arr :Vector[Json]) =>
          val mongoItems = arr.toList.map { (arrItem :Json) => jsonToMongo(arrItem) }
          builder += (key -> MongoDBList(mongoItems :_*))
        }
      case j if j.isObject =>
        j.asObject.foreach { _ =>
          val mongoObj = jsonToMongo(j)
          builder += (key -> mongoObj)
        }
      case j if j.isBoolean => j.as[Boolean].toOption.foreach { boolValue => builder += (key -> boolValue) }
      case j if j.isString => j.as[String].toOption.foreach { stringValue => builder += (key -> stringValue) }
    }}
    builder.result()
  }

  def emailRowToMongoDBObject(emailId :String, status :String, json :Json) :Email = {

    Email(
      emailId = emailId,
      caseId = None,
      caseRef = None,
      date = DateTime.parse(json.hcursor.downField("date").as[String].toOption.get),
      recipient = json.hcursor.downField("recipient").as[String].toOption.getOrElse(""),
      subject = json.hcursor.downField("subject").as[String].toOption.getOrElse(""),
      text = json.hcursor.downField("text").as[String].toOption.getOrElse(""),
      html = json.hcursor.downField("html").as[String].toOption.getOrElse(""),
      status = status,
      emailType = json.hcursor.downField("type").as[String].toOption.getOrElse(""),
      cc = json.hcursor.downField("cc").as[List[String]].toOption.getOrElse(List.empty),
      personalisations = json.hcursor.downField("personalisations").as[Json].toOption.map { json => jsonToMongo(json) }
    )
  }

  def getWaitingEmails() :fs2.Stream[IO, Email] = {
    val selectSQL = sql"SELECT emailId, status, email FROM email WHERE status = $varchar".query(varchar ~ varchar ~ jsonb)
      .map { case emailId ~ status ~ json => (emailId, status, json) }

    val ses :fs2.Stream[IO, skunk.Session[IO]] = fs2.Stream.resource(makeSession())
    val pc :fs2.Stream[IO, skunk.PreparedQuery[IO, String, (String, String, Json)]] = ses.flatMap { s => fs2.Stream.eval(s.prepare(selectSQL)) }
    val stream :fs2.Stream[IO, (String, String, Json)] = pc.flatMap { p => p.stream(STATUS_WAITING, 512) }
    stream.map { case (emailId, status, json) => emailRowToMongoDBObject(emailId, status, json) }
  }

  def updateStatus(email :Email, emailSentResult :EmailSentResult) :IO[EmailSentResult] = {

    emailSentResult match {
      case s @ Sent(newText, newHtml) =>
        logger.info("Marking email as sent")
        updateStatusWithContent(email.emailId, STATUS_SENT, newText.getOrElse(""), newHtml.getOrElse(""))
          .map { _ => s }

      case w @ Waiting =>
        logger.info("Marking not sent")
        updateStatusNoContent(email.emailId, STATUS_WAITING)
          .map { _ => w }

      case t @ TransientError(err) =>
        logger.error(s"Error sending email: $err")
        updateStatusNoContent(email.emailId, STATUS_WAITING)
          .map { _ => t }

      case e @ ExhaustedRetries =>
        logger.error(s"Giving up sending email: ${email.emailId}")
        updateStatusNoContent(email.emailId, STATUS_EXHAUSTED)
          .map { _ => e }

      case p @ PartialError(err) =>
        logger.error(s"Partial error, unable to retry: ${email.emailId} $err")
        updateStatusNoContent(email.emailId, STATUS_PARTIAL)
          .map { _ => p }

      case ea @ EmailAddressError(err) =>
        logger.error(s"Error with email address: $err")
        updateStatusNoContent(email.emailId, STATUS_EMAIL_ADDRESS_ERROR)
          .map { _ => ea }

      case unknown =>
        logger.error(s"Unexpected Error class reported: $unknown. Passing through as EmailAddressError")
        updateStatusNoContent(email.emailId, unknown.toString)
          .map { _ => EmailAddressError(unknown.toString) }
    }
  }

  // TODO: Trying to rush this out for the beta. Haven't got a safe
  // mechanism to embed the text/html safely into a JSON object using
  // Postgres SQL syntax. For now, we don't save text/html from generated
  // emails back into the DB. This is left open for further work.
  // I have opened DPSPS-1420 to re-implement this feature.

  private def updateStatusWithContent(emailId :String, newStatus :String, newText :String, newHtml :String) :IO[String] =
    updateStatusNoContent(emailId, newStatus)

  private def updateStatusNoContent(emailId :String, newStatus :String) :IO[String] = {
    val updateCommand :Command[String] =
      newStatus match {
        case STATUS_WAITING => sql"""UPDATE email SET status='WAITING', email = jsonb_set(email, '{status}', '"WAITING"') WHERE emailId=$varchar""".command
        case STATUS_SENT => sql"""UPDATE email SET status='SENT', email = jsonb_set(email, '{status}', '"SENT"') WHERE emailId=$varchar""".command
        case _ => sql"""UPDATE email SET status='ERROR', email = jsonb_set(email, '{status}', '"ERROR"') WHERE emailId=$varchar""".command
      }

    makeSession().use { session =>
      val result = session.prepare(updateCommand).flatMap { preparedCommand =>
        preparedCommand.execute(emailId)
      }
      result.attempt.map {
        case Left(throwable) =>
          logger.error(s"UpdateStatusNoContent returned error: $throwable")
          throw throwable
        case Right(completion) =>
          logger.info(s"Email Status updated. Returned $completion from ${updateCommand.sql}")
          newStatus
      }
    }
  }

  // For RT/GE, the emails are far too complicated to give up the case: syntax feature,
  // but for non-mongo systems, the feature is no longer supported outside Mongo.
  def caseObjectFromEmail(email :Email)(implicit appContext :AppContext) :IO[Either[GovNotifyError, Option[MongoDBObject]]] =
    IO.delay(Right(None))

  def parentObjectFromCaseObject(caseObj :MongoDBObject)(implicit appContext :AppContext) :IO[Either[GovNotifyError, Option[MongoDBObject]]] =
    IO.delay(Right(None))
}
