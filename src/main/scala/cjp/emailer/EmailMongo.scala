package cjp.emailer

import com.mongodb.casbah.MongoClientURI
import com.typesafe.config.ConfigFactory
import uk.gov.homeoffice.configuration.HasConfig
import uk.gov.homeoffice.mongo.casbah.Mongo

trait EmailMongo extends Mongo {
  lazy val db = EmailMongo.db
}

object EmailMongo extends HasConfig {
  lazy val db = Mongo db MongoClientURI(Config.config.generateMongoURL())
}