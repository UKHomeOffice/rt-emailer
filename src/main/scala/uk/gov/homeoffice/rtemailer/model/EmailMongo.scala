package uk.gov.homeoffice.rtemailer.model

import uk.gov.homeoffice.rtemailer.Main
import uk.gov.homeoffice.mongo.casbah.Mongo
import com.mongodb.casbah.MongoClientURI

// required for interaction with rtp-email-lib
trait EmailMongo extends Mongo /* with StrictLogging */ {

  val dbHost = Main.config.getString("db.mongo.host")
  val dbName = Main.config.getString("db.mongo.name")
  val dbUser = Main.config.getString("db.mongo.user")
  val dbPassword = Main.config.getString("db.mongo.password")
  val dbParams = Main.config.getString("db.mongo.params")
  val mongoConnectionString = dbUser.isEmpty match {
    case false =>
      s"mongodb://$dbUser:$dbPassword@$dbHost/$dbName?$dbParams"
    case true =>
      val cs = s"mongodb://$dbHost/$dbName"
      cs
  }

  lazy val mongoDB = Mongo.mongoDB(MongoClientURI(mongoConnectionString))

}

