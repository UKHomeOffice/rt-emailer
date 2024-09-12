package uk.gov.homeoffice.rtemailer.database

import munit.CatsEffectSuite
import cats.effect._
import org.joda.time.DateTime
import com.typesafe.config.ConfigFactory
import uk.gov.homeoffice.rtemailer.model.AppContext
import org.bson.types.ObjectId
import uk.gov.homeoffice.rtemailer.emailsender._

import com.mongodb.casbah.commons.MongoDBObject

class LegacyMongoDatabaseSpec extends CatsEffectSuite {

  test("parent lookup inc. database calls works") {

    val childSubmissionId = new ObjectId()
    val now = DateTime.now()

    val childCaseObject = MongoDBObject(
      "_id" -> childSubmissionId,
      "registeredTravellerNumber" -> "RT-CHILD",
      "name" -> "Dillon",
      "parentRegisteredTravellerNumber" -> "RT-PARENT"
    )

    val parentCaseObject = MongoDBObject(
      "registeredTravellerNumber" -> "RT-PARENT",
      "name" -> "Jullian"
    )

    val emailObject = MongoDBObject(
      "caseId" -> childSubmissionId,
      "recipient" -> "test@example.com",
      "status" -> "WAITING",
      "type" -> "Under 18 Application Receieved",
      "cc" -> List.empty[String],
      "subject" -> "Under 18"
    )

    val config = ConfigFactory.parseString("""
      app {
        templateDebug = false
      }
      db {
        backend = "LegacyMongoDatabase"
        mongo {
          host = "localhost"
          host = ${?DB_HOST}
          name = "rt-emailer"
          user = ""
          password = ""
          params = ""
        }
      }
      govNotify {
        apiKey = "1234"
        apiKey2 = ""
        caseTable = "submissions"
        staticPersonalisations {
          rt_customer_host = "https://rt.example.com"
          ge_customer_host = "https://ge.example.com"
          rtge_caseworker_host = "https://rtgecaseworker.example.com"
          rt_new_application_fee = 7000
        }
      }
    """).resolve()

    val legacyMongoDatabase = new LegacyMongoDatabase(config)

    val appContext = AppContext(
      nowF = { () => now },
      config = config,
      database = legacyMongoDatabase,
      statsDClient = null
    )

    legacyMongoDatabase.mongoDB_("submissions").insert(childCaseObject)
    legacyMongoDatabase.mongoDB_("submissions").insert(parentCaseObject)
    legacyMongoDatabase.mongoDB_("email").insert(emailObject)

    // Test begins here.
    val govNotifyEmailSender = new GovNotifyEmailSender()(appContext)
    val result = govNotifyEmailSender.buildParentPersonalisations(List("parent:name"), Some(new MongoDBObject(childCaseObject)), "my template").unsafeRunSync()
    assertEquals(result.right.get, Map("parent:name" -> "Jullian"))
  }
}
