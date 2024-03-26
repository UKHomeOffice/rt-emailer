package uk.gov.homeoffice.rtemailer.model

import uk.gov.homeoffice.rtemailer.Main
import uk.gov.homeoffice.mongo.casbah.Mongo
//import com.typesafe.scalalogging.StrictLogging
import com.mongodb.casbah.MongoClientURI

// required for interaction with rtp-email-lib
trait EmailMongo extends Mongo /* with StrictLogging */ {

  val dbHost = Main.config.getString("db.host")
  val dbName = Main.config.getString("db.name")
  val dbUser = Main.config.getString("db.user")
  val dbPassword = Main.config.getString("db.password")
  val dbParams = Main.config.getString("db.params")
  val mongoConnectionString = dbUser.isEmpty match {
    case false =>
      //logger.info(s"mongo connection string: mongodb://$dbUser:*******@$dbHost/$dbName?$dbParams")
      s"mongodb://$dbUser:$dbPassword@$dbHost/$dbName?$dbParams"
    case true =>
      val cs = s"mongodb://$dbHost/$dbName"
      //logger.info(s"mongo connection string: $cs")
      cs
  }

  lazy val mongoDB = Mongo.mongoDB(MongoClientURI(mongoConnectionString))

}

