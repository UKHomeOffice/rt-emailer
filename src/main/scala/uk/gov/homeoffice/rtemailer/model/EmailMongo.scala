package uk.gov.homeoffice.rtemailer.model

import uk.gov.homeoffice.rtemailer.Main
import uk.gov.homeoffice.mongo.casbah.Mongo

// required for interaction with rtp-email-lib
trait EmailMongo extends Mongo {
  lazy val mongoDB = Main.mongoDB
}
