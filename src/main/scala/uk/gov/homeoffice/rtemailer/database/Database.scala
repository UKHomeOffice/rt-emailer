package uk.gov.homeoffice.rtemailer.database

import cats.effect.IO
import com.typesafe.config.Config
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import uk.gov.homeoffice.rtemailer.model._
import uk.gov.homeoffice.mongo.casbah.MongoDBObject
import uk.gov.homeoffice.domain.core.lock.Lock

trait Database {
  val name :String

  def obtainLock() :IO[Lock]
  def releaseLock(lock :Lock) :IO[Unit]

  def getWaitingEmails() :fs2.Stream[IO, Email]
  def updateStatus(email :Email, newStatus :EmailSentResult) :IO[EmailSentResult]

  def caseObjectFromEmail(email :Email)(implicit appContext :AppContext) :IO[Either[GovNotifyError, Option[MongoDBObject]]]
  def parentObjectFromCaseObject(caseObj :MongoDBObject)(implicit appContext :AppContext) :IO[Either[GovNotifyError, Option[MongoDBObject]]]
}

object Database {

  def make(config :Config) :Database =
    config.getString("db.backend") match {
      case "LegacyMongoDatabase" => new LegacyMongoDatabase(config)
      case "PostgresDatabase" => new PostgresDatabase(config)
      case unknown => throw new Exception(s"Config db.backend must be LegacyMongoDatabase or PostgresDatabase. Not $unknown")
    }

}
