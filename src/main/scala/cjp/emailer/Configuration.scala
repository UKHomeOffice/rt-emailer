package cjp.emailer

import scala.beans.BeanProperty
import domain.core.mongo.MongoConnector
import com.mongodb.casbah.MongoClientURI

class Configuration {
  @BeanProperty var dbHost: String = null
  @BeanProperty var dbName: String = null
  @BeanProperty var dbUser: String = null
  @BeanProperty var dbPassword: String = null
  @BeanProperty var smtpServerHost: String = null
  @BeanProperty var smtpServerPort: Int = 25
  @BeanProperty var smtpServerUsername: String = null
  @BeanProperty var smtpServerPassword: String = null
  @BeanProperty var sender: String = null
  @BeanProperty var senderName: String = null
  @BeanProperty var pollingFrequency: Int = 5
  @BeanProperty var loggerxml: String = null
  @BeanProperty var port: Int = 8085

  override def toString: String =
    s"mongodb host: $dbHost db: $dbName smtp host: $smtpServerHost port: $smtpServerPort " + s"user: $smtpServerUsername password: $smtpServerPassword"

  def toSmtpConfig = {
    new SmtpConfig(port = smtpServerPort,
                   host = smtpServerHost,
                   user = smtpServerUsername,
                   password = smtpServerPassword)
  }

  def getMongoDBConnector: MongoConnector = {
    val mongoURL = generateMongoURL()
    new MongoConnector(mongoURL.getURI + getDbName)
  }

  def generateMongoURL() = {
    if (getDbUser != "")
      MongoClientURI("mongodb://" + getDbUser + ":" + getDbPassword + "@" + getDbHost + "/")
    else
      MongoClientURI("mongodb://" + getDbHost + "/")
  }
}