package uk.gov.homeoffice.rtemailer.database

import cats.effect.IO
import cats.effect.kernel.Resource
import com.typesafe.config.Config
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import uk.gov.homeoffice.rtemailer.model._
import com.mongodb.casbah.commons.MongoDBObject
import uk.gov.homeoffice.domain.core.lock.Lock

trait Database {
  def name() :String

  def obtainLock() :IO[Lock]
  def releaseLock(lock :Lock) :IO[Unit]

  def getWaitingEmails() :fs2.Stream[IO, Email]
  def updateStatus(email :Email, newStatus :EmailSentResult) :IO[EmailSentResult]

  def caseObjectFromEmail(email :Email)(implicit appContext :AppContext) :IO[Either[GovNotifyError, Option[MongoDBObject]]]
  def parentObjectFromCaseObject(caseObj :MongoDBObject)(implicit appContext :AppContext) :IO[Either[GovNotifyError, Option[MongoDBObject]]]
}

object Database {

  def make(config :Config) :Database =
    new LegacyMongoDatabase(config)
}
