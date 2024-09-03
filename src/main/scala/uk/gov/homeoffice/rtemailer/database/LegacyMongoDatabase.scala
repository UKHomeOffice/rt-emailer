package uk.gov.homeoffice.rtemailer.database

import uk.gov.homeoffice.rtemailer.model._
import uk.gov.homeoffice.domain.core.email.{Email, EmailRepository}
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import uk.gov.homeoffice.rtemailer.model._
import cats.effect.IO
import uk.gov.homeoffice.rtemailer.Util._
import java.net.InetAddress

import uk.gov.homeoffice.domain.core.lock._
import com.mongodb.casbah.MongoClientURI
import uk.gov.homeoffice.mongo.casbah.Mongo
import com.mongodb.casbah.MongoDB

import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import scala.util.Try
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

class LegacyMongoDatabase(config :Config) extends Database with StrictLogging {

  val dbHost = config.getString("db.mongo.host")
  val dbName = config.getString("db.mongo.name")
  val dbUser = config.getString("db.mongo.user")
  val dbPassword = config.getString("db.mongo.password")
  val dbParams = config.getString("db.mongo.params")
  val mongoConnectionString = dbUser.isEmpty match {
    case false =>
      logger.info(s"mongo connection string: mongodb://$dbUser:*******@$dbHost/$dbName?$dbParams")
      s"mongodb://$dbUser:$dbPassword@$dbHost/$dbName?$dbParams"
    case true =>
      val cs = s"mongodb://$dbHost/$dbName"
      logger.info(s"mongo connection string: $cs")
      cs
  }

  val mongoDB_ = Mongo.mongoDB(MongoClientURI(mongoConnectionString))

  def name() = "Legacy Mongo Database"

  lazy val processLockRepository = new ProcessLockRepository with EmailMongo { override lazy val mongoDB :MongoDB = mongoDB_ }
  lazy val host = InetAddress.getLocalHost.getHostName
  val lockName = "rt-emailer"

  lazy val emailRepository = new EmailRepository with EmailMongo
  
  def obtainLock() :IO[Lock] = { IO.delay(processLockRepository.obtainLock(lockName, host).getOrElse(throw new Exception(s"Unable to aquire lock"))) }
  def releaseLock(lock :Lock) :IO[Unit] = { IO.delay(processLockRepository.releaseLock(lock)) }

  def getWaitingEmails() :fs2.Stream[IO, Email] = {
    val emailList :List[Email] = emailRepository.findByStatus(STATUS_WAITING)
    fs2.Stream.fromIterator[IO](emailList.iterator, 512)
  }

  def updateStatus(email :Email, emailSentResult :EmailSentResult) :IO[EmailSentResult] = {
    IO.delay {

      emailSentResult match {
        case Sent(newText, newHtml) =>
          logger.info("Marking email as sent")
          emailRepository.updateStatus(email.emailId, STATUS_SENT, newText, newHtml)
          Sent(newText, newHtml)

        case Waiting =>
          logger.info("Marking not sent")
          emailRepository.updateStatus(email.emailId, STATUS_WAITING)
          Waiting

        case TransientError(err) =>
          logger.error(s"Error sending email: $err")
          emailRepository.updateStatus(email.emailId, STATUS_WAITING)
          TransientError(err)

        case ExhaustedRetries =>
          logger.error(s"Giving up sending email: ${email.emailId}")
          emailRepository.updateStatus(email.emailId, STATUS_EXHAUSTED)
          ExhaustedRetries

        case PartialError(err) =>
          logger.error(s"Partial error, unable to retry: ${email.emailId} $err")
          emailRepository.updateStatus(email.emailId, STATUS_PARTIAL)
          PartialError(err)

        case EmailAddressError(err) =>
          logger.error(s"Error with email address: $err")
          emailRepository.updateStatus(email.emailId, STATUS_EMAIL_ADDRESS_ERROR)
          EmailAddressError(err)

        case unknown =>
          logger.error(s"Unexpected Error class reported: $unknown. Passing through as EmailAddressError")
          emailRepository.updateStatus(email.emailId, unknown.toString)
          EmailAddressError(unknown.toString)
      }
    }
  }

  def caseObjectFromEmail(email :Email)(implicit appContext :AppContext) :IO[Either[GovNotifyError, Option[MongoDBObject]]] = {
    lazy val caseTable :String = config.getString("govNotify.caseTable")

    email.caseId match {
      case Some(caseId) => IO.blocking(Try(mongoDB_(caseTable).findOne(MongoDBObject("_id" -> new ObjectId(caseId)))).toEither match {
          case Left(exc) =>
            appContext.updateAppStatus(_.recordDatabaseError(exc.getMessage))
            Left(GovNotifyError(s"Database error looking up case from email: ${exc.getMessage()}"))
          case Right(maybeObj) =>
            appContext.updateAppStatus(_.markDatabaseOk)
            Right(maybeObj.map(new MongoDBObject(_)))
          }
        )
      case None => IO.delay(Right(None))
    }
  }

  def parentObjectFromCaseObject(caseObj :MongoDBObject)(implicit appContext :AppContext) :IO[Either[GovNotifyError, Option[MongoDBObject]]] = {
    lazy val caseTable :String = config.getString("govNotify.caseTable")

    extractDBField(caseObj, "latestApplication.parentRegisteredTravellerNumber") match {
      case Some(parentRT) => IO.blocking {
        parentRT.stringValue() match {
          case Right(parentRTString) =>
            Try(mongoDB_(caseTable).findOne(MongoDBObject("registeredTravellerNumber"-> parentRTString))).toEither
              .map { _.map(new MongoDBObject(_)) }
              .left.map { exc =>
                logger.info(s"Database error looking up parent case (caseId: ${caseObj.get("_id").toString}): ${exc.getMessage()}")
                GovNotifyError(s"Database error looking up parent case from case: ${exc.getMessage()}")
              }
          case Left(exc) =>
            logger.info(s"latestApplication.parentRegisteredTravellerNumber was not a string (${caseObj.get("_id")})")
            Right(None)
        }
      }
      case None =>
        logger.info(s"No latestApplication.parentRegisteredTravellerNumber in caseObj (${caseObj.get("_id")})")
        IO.delay(Right(None))
    }
  }
}
