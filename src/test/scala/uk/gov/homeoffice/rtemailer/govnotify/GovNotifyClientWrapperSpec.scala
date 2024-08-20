package uk.gov.homeoffice.rtemailer.govnotify

import munit.CatsEffectSuite
import cats.effect._
import org.bson.types.ObjectId
import org.joda.time.DateTime
import com.typesafe.config.ConfigFactory
import uk.gov.homeoffice.rtemailer.model.AppContext
import uk.gov.service.notify.{Template, TemplateList, NotificationClient}
import scala.collection.JavaConverters._
import uk.gov.homeoffice.mongo.casbah.MongoDBObject
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.rtemailer.model._
import uk.gov.service.notify.{Template, SendEmailResponse}

class GovNotifyClientWrapperSpec extends CatsEffectSuite {

  val testTemplate1 = new Template(s"""{
    "id":"${java.util.UUID.randomUUID()}",
    "name":"HelloEmail",
    "type":"email",
    "created_at":"2000-01-01T00:00:00.000Z",
    "updated_at":"2000-01-01T00:00:00.000Z",
    "version":3,
    "body":"hello customer",
    "subject":"hello",
    "personalisation":{
      "case:name":"",
      "case:boolField:lower:bool:not":"",
      "case:dateOfBirth:plusYears18:beforeNow":"",
      "config:rt_customer_host":"",
      "parent:details.age:minusN18:gt10":""
    }
  }""")

  val testTemplate2 = new Template(s"""{
    "id":"${java.util.UUID.randomUUID()}",
    "name":"InfoEmail",
    "type":"email",
    "created_at":"2000-01-01T00:00:00.000Z",
    "updated_at":"2000-01-01T00:00:00.000Z",
    "version":3,
    "body":"hello customer",
    "subject":"hello",
    "personalisation":{"name":"string"}
  }""")

  test("getAllTemplates retains client with template") {

    val testAppContext = new AppContext(
      nowF = () => DateTime.parse("2024-01-01T01:02:03"),
      ConfigFactory.parseString("""
        app {
          templateDebug = false
        }
        govNotify {
          apiKey = "1234"
          apiKey2 = "5678"
        }
      """),
      null,
      null
    )

    val fakeNotifyClient1 = new NotificationClient("") {
      override def getAllTemplates(templateType :String) :TemplateList = new TemplateList("""{ "templates": [] }""") {
        override def getTemplates() :java.util.List[Template] = List(testTemplate1).asJava
      }
    }

    val fakeNotifyClient2 = new NotificationClient("") {
      override def getAllTemplates(templateType :String) :TemplateList = new TemplateList("""{ "templates": [] }""") {
        override def getTemplates() :java.util.List[Template] = List(testTemplate2).asJava
      }
    }

    val govNotifyClient = new GovNotifyClientWrapper()(testAppContext) {
      override val notifyClient1 = fakeNotifyClient1
      override val notifyClient2 = Some(fakeNotifyClient2)
    }

    val allTemplates = govNotifyClient.getAllTemplates().unsafeRunSync()
    allTemplates match {
      case Left(gnErr) => fail(s"Fetching templates failed with $gnErr")
      case Right(list) =>
        assertEquals(list.map(_.template), List(testTemplate1, testTemplate2))
        assertEquals(list.headOption.map(_.client), Some(govNotifyClient.notifyClient1))
        assertEquals(list.lastOption.map(_.client), govNotifyClient.notifyClient2)
    }
  }

  test("sendEmail call sends emails to everyone in cc list") {

    // track calls to sendOneEmail
    case class EmailSent(recipient :String, reference :String)
    var testCounter :List[EmailSent] = List()


    val testAppContext = new AppContext(
      nowF = () => DateTime.parse("2024-01-01T01:02:03"),
      ConfigFactory.parseString("""
        app {
          templateDebug = false
        }
        govNotify {
          apiKey = "1234"
          apiKey2 = ""
        }
      """),
      null,
      null
    )

    val govNotifyClient = new GovNotifyClientWrapper()(testAppContext) {
      override def sendOneEmail(recipient :String, emailReference :String, twc :TemplateWC, allPersonalisations :Map[String, String]) :IO[Either[GovNotifyError, SendEmailResponse]] = {
          testCounter = testCounter ++ List(EmailSent(recipient, emailReference))
          IO.delay(Right(new SendEmailResponse(s"""{
            "id":"37370573-5a48-4f10-aed7-b632fb48bcf4",
            "reference":"3231232313",
            "content":{
              "body" : "new text",
              "subject":"end-to-end test scenario"
            },
            "template":{
              "id":"37370573-5a48-4f10-aed7-b632fb48bcf4",
              "version":3,
              "uri":"https://gov-notify.example.com/uri/test"
            }
          }""")))
        }
    }

    val emailId = new ObjectId().toHexString

    val email = new Email(
      emailId,
      None,
      None,
      testAppContext.nowF(),
      "main-recipient@example.com",
      "",
      "",
      "",
      "WAITING",
      emailType = "HelloMyFriend",
      cc = List(
        "two@example.com",
        "three@test.com"
      ),
      personalisations=Some(MongoDBObject("bool" -> true))
    )

    // call sendEmail
    val result = govNotifyClient.sendEmail(email, TemplateWC(new Template(s"""{
        "id":"${java.util.UUID.randomUUID()}",
        "name":"TemplateWithPersonalisations",
        "type":"email",
        "created_at":"2000-01-01T00:00:00.000Z",
        "updated_at":"2000-01-01T00:00:00.000Z",
        "version":3,
        "body":"hello customer",
        "subject":"hello",
        "personalisation":{"email:personalisations.name":"string"}
      }"""), null),
      Map.empty
    ).unsafeRunSync()

    assert(result.isRight)
    assertEquals(testCounter, List(
      EmailSent("main-recipient@example.com", emailId),
      EmailSent("two@example.com", emailId + "[cc=1]"),
      EmailSent("three@test.com", emailId + "[cc=2]")
    ))
  }
}
