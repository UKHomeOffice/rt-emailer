package cjp.emailer

import com.mongodb.casbah.MongoClientURI
import uk.gov.homeoffice.configuration.HasConfig
import uk.gov.homeoffice.mongo.casbah.Mongo

trait EmailMongo extends Mongo {
  lazy val mongoDB = EmailMongo.mongoDB
}

object EmailMongo extends HasConfig {
  lazy val mongoDB =   Mongo.mongoDB(MongoClientURI(Main.generateMongoURL()))
}