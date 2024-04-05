package uk.gov.homeoffice.rtemailer.database

import munit.CatsEffectSuite
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.rtemailer.model._
import io.circe._
import io.circe.syntax._
import com.mongodb.casbah.commons.{MongoDBObject, MongoDBList}
import com.mongodb.DBObject
import org.bson.types.ObjectId
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime

class PostgresDatabaseSpec extends CatsEffectSuite {

  test("personalisation JSON sections are correctly mapped to MongoDBObjects") {

    val testObject = Json.obj(
      "stringField" -> Json.fromString("value"),
      "intField" -> Json.fromInt(1),
      "boolField" -> Json.fromBoolean(true),
      "nested" -> Json.obj(
        "item" -> Json.fromString("found")
      ),
      "array" -> Json.arr(Json.obj("number" -> Json.fromInt(1)), Json.obj("number" -> Json.fromInt(2)))
    )

    val builder = MongoDBObject.newBuilder
    builder += ("stringField" -> "value")
    builder += ("intField" -> 1)
    builder += ("boolField" -> true)
    builder += ("nested" -> MongoDBObject("item" -> "found"))
    builder += ("array" -> MongoDBList(MongoDBObject("number" -> 1), MongoDBObject("number" -> 2)))

    val expectedOutput = builder.result()

    val config = ConfigFactory.parseString(""" """)
    val postgresDatabase = new PostgresDatabase(config)

    assertEquals(expectedOutput, postgresDatabase.jsonToMongo(testObject))

  }

  test("decoding an email from postgres into an Email instance works, including a toDBObject call") {

    val config = ConfigFactory.parseString(""" """)
    val postgresDatabase = new PostgresDatabase(config)

    val validObjectId = new ObjectId().toString

    val testInput = Json.obj(
      "date" -> Json.fromString("2023-06-02T02:04:06Z"),
      "recipient" -> Json.fromString("my-email@example.com"),
      "subject" -> Json.fromString("email from system"),
      "text" -> Json.fromString("text"),
      "html" -> Json.fromString("<html>hello</html>"),
      "status" -> Json.fromString("WAITING"),
      "type" -> Json.fromString("New customer email"),
      "cc" -> Json.arr(),
      "personalisations" -> Json.obj("customer-name" -> Json.fromString("Harry and Hugo"))
    )

    val correctOutput = Email(
      emailId = validObjectId,
      caseId = None,
      caseRef = None,
      date = DateTime.parse("2023-06-02T02:04:06Z"),
      recipient = "my-email@example.com",
      subject = "email from system",
      text = "text",
      html = "<html>hello</html>",
      status = "WAITING",
      emailType = "New customer email",
      cc = List.empty,
      personalisations = Some(MongoDBObject("customer-name" -> "Harry and Hugo"))
    )

    assertEquals(correctOutput, postgresDatabase.emailRowToMongoDBObject(validObjectId, "WAITING", testInput))
  }

}

