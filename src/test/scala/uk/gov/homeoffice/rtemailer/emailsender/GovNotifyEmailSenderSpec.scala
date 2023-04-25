package uk.gov.homeoffice.rtemailer.emailsender

import munit.CatsEffectSuite
import cats.effect._
import org.joda.time.DateTime
import com.typesafe.config.ConfigFactory
import uk.gov.homeoffice.rtemailer.model.AppContext

import com.mongodb.casbah.commons.MongoDBObject
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import uk.gov.homeoffice.rtemailer.model._
import uk.gov.service.notify.{Template, TemplatePreview, SendEmailResponse}

class govNotifyEmailSenderSpec extends CatsEffectSuite {

  val testAppContext = new AppContext(
    nowF = () => DateTime.parse("2024-01-01T01:02:03"),
    ConfigFactory.parseString("""
      app {
        templateDebug = false
      }
      govNotify {
        apiKey = "1234"
        caseTable = "submissions"
        staticPersonalisations {
          rt_customer_host = "https://rt.example.com"
          ge_customer_host = "https://ge.example.com"
          rtge_caseworker_host = "https://rtgecaseworker.example.com"
          rt_new_application_fee = 7000
        }
      }
    """),
    null
  )

  val fakeNotifyWrapper = new GovNotifyClientWrapper()(testAppContext) {
    override def getAllTemplates() :IO[Either[GovNotifyError, List[Template]]] = {
      IO.delay(Right(List(

        // This is a realistic example which is explored in the final test
        // which runs through the complete scenario, including parents, config
        // and more.
        new Template(s"""{
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
        }"""),
        new Template(s"""{
          "id":"${java.util.UUID.randomUUID()}",
          "name":"InfoEmail",
          "type":"email",
          "created_at":"2000-01-01T00:00:00.000Z",
          "updated_at":"2000-01-01T00:00:00.000Z",
          "version":3,
          "body":"hello customer",
          "subject":"hello",
          "personalisation":{"name":"string"}
        }"""),
      )))
    }

    override def generateTemplatePreview(template :Template, personalisations :Map[String, String]) :IO[Either[GovNotifyError, TemplatePreview]] = {

      template.getName() match {
        case "HelloEmail" =>

          // GovNotify's JSON library validates the JSON payload at _compile time_
          // and adds a big layer of trickyness to writing static strings here.
          val templatePreview = new TemplatePreview(s"""{
            "id":"37370573-5a48-4f10-aed7-b632fb48bcf4",
            "version":4,
            "type":"HelloEmail",
            "body":"new text",
            "subject":"end-to-end test scenario",
          }""")

          val stableList = personalisations.map { case (k, v) => s"$k=$v"}.toList.sorted.mkString("\n")
          val personalisationString = s"Expanded Parameters:\n$stableList"
          templatePreview.setHtml(personalisationString)
          IO.delay(Right(templatePreview))

        case x => IO.delay(Left(GovNotifyError(s"bad template: $x")))
      }
    }

    override def sendEmail(email :Email, template: Template, allPersonalisations :Map[String, String]) :IO[Either[GovNotifyError, SendEmailResponse]] = {

      email.recipient match {
        case "fail@example.com" =>
          IO.delay(Left(GovNotifyError(s"No authority to send email")))
        case _ =>
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
  }

  val fakeMongoWrapper = new GovNotifyMongoWrapper()(testAppContext) {
    override def caseObjectFromEmail(email :Email) :IO[Either[GovNotifyError, Option[MongoDBObject]]] = {
      email.emailType match {
        case "HelloEmail" =>
          // part of the end-to-end scenario
          IO.delay(Right(Some(new MongoDBObject(MongoDBObject(
            "name" -> "phillip",
            "dateOfBirth" -> DateTime.parse("2020-01-02T11:11:11Z"),
            "boolField" -> "True", // test demostrates how to smooth a string like this into "yes" manually
            "latestApplication" -> MongoDBObject("parentRegisteredTravellerNumber" -> "RT123")
          )))))
        case _ =>
          IO.delay(Right(Some(new MongoDBObject(MongoDBObject(
            "name" -> "phillip",
            "details" -> MongoDBObject("age" -> "17")
          )))))
      }
    }

    override def parentObjectFromCase(caseObj :MongoDBObject) :IO[Either[GovNotifyError, Option[MongoDBObject]]] = {
      // pretend scenario
      caseObj.getAs[String]("latestApplication.parentRegisteredTravellerNumber") match {
        case Some(rtn) =>
          IO.delay(Right(Some(new MongoDBObject(MongoDBObject(
            "registeredTravellerNumber" -> rtn,
            "name" -> "mary",
            "details" -> MongoDBObject("age" -> 66)
          )))))
        case None => IO.delay(Right(None))
      }
    }
  }

  val govNotifyEmailSender = new GovNotifyEmailSender()(testAppContext) {
    override lazy val notifyClientWrapper = fakeNotifyWrapper
    override lazy val mongoWrapper = fakeMongoWrapper
  }

  test("Use Gov Notify if email type matches the name of a template") {

    val testEmail = new Email("id1", None, None, testAppContext.nowF(), "test@example.com", "", "", "", "WAITING", emailType = "HelloEmail", Nil)
    val unknownType = new Email("id1", None, None, testAppContext.nowF(), "test@example.com", "", "", "", "WAITING", emailType = "UnknownType", Nil)

    assertEquals(govNotifyEmailSender.useGovNotify(testEmail).unsafeRunSync(), Right(true))
    assertEquals(govNotifyEmailSender.useGovNotify(unknownType).unsafeRunSync(), Right(false))
  }

  test("extracting parameters from case objects work") {
    val testCase = new MongoDBObject(MongoDBObject(
      "name" -> "phillip",
      "details" -> MongoDBObject("age" -> 17)
    ))

    val testPersonalisations = List("case:name", "case:details.age")

    val result = govNotifyEmailSender.buildCasePersonalisations(testPersonalisations, testCase, "test-template")
    result match {
      case Left(err) =>
        fail(s"unexpected failure in test: $err")
      case Right(personalisationMap) =>
        assertEquals(
          personalisationMap,
          Map(
            "case:name" -> "phillip",
            "case:details.age" -> "17"
          )
        )
    }
  }

  test("extracting parameters from a parent object works (in isolation)") {
    val testCase = new MongoDBObject(MongoDBObject(
      "latestApplication" -> MongoDBObject("parentRegisteredTravellerNumber" -> "RTAA122"),
      "name" -> "phillip",
      "details" -> MongoDBObject("age" -> 17)
    ))

    val testPersonalisations = List("case:name", "parent:name", "parent:details.age")

    val result = govNotifyEmailSender.buildParentPersonalisations(testPersonalisations, testCase, "test-template")
    result.unsafeRunSync() match {
      case Left(err) =>
        fail(s"unexpected failure in test: $err")
      case Right(personalisationMap) =>
        assertEquals(
          personalisationMap,
          Map(
            // case:name disappears because we don't merge buildCasePersonalisatsions.. in this
            // test
            "parent:name" -> "mary",
            "parent:details.age" -> "66"
          )
        )
    }
  }

  test("extracting parameters from a config object works") {
    val testPersonalisations = List("config:rt_customer_host", "config:rt_new_application_fee", "config:noexists")

    val result = govNotifyEmailSender.buildConfigPersonalisations(testPersonalisations, "test-config-template")
    assertEquals(result, Right(Map(
      "config:noexists" -> "",
      "config:rt_new_application_fee" -> "7000",
      "config:rt_customer_host" -> "https://rt.example.com"
    )))
  }

  test("when a personalisation is missing, empty string is returned") {
    val testCase = new MongoDBObject(MongoDBObject(
      "name" -> "phillip",
      "details" -> MongoDBObject("age" -> 17)
    ))

    val testPersonalisations = List("case:givenYes", "case:total.sum")

    val result = govNotifyEmailSender.buildCasePersonalisations(testPersonalisations, testCase, "test-template-empty")
    result match {
      case Left(err) =>
        fail(s"unexpected failure in test: $err")
      case Right(personalisationMap) =>
        assertEquals(
          personalisationMap,
          Map(
            "case:givenYes" -> "",
            "case:total.sum" -> ""
          )
        )
    }
  }

  test("when the field is a date, it becomes formatted/stringified as one") {
    val testCase = new MongoDBObject(MongoDBObject(
      "time" -> DateTime.parse("2020-03-01T12:33:44Z")
    ))

    val testPersonalisations = List("case:time")

    val result = govNotifyEmailSender.buildCasePersonalisations(testPersonalisations, testCase, "test-template-empty")
    assertEquals(result, Right(Map("case:time" -> "01 March 2020")))
  }

  test("when the field is a boolean it is formatted as yes/no") {
    val testCase = new MongoDBObject(MongoDBObject(
      "bool" -> true
    ))

    val testPersonalisations = List("case:bool")

    val result = govNotifyEmailSender.buildCasePersonalisations(testPersonalisations, testCase, "test-template-empty")
    result match {
      case Left(err) =>
        fail(s"unexpected failure in test: $err")
      case Right(personalisationMap) =>
        assertEquals(
          personalisationMap,
          Map("case:bool" -> "yes")
        )
    }
  }

  test("full good end-to-end scenario") {

    val helloTypeEmail = new Email(
      "id1",
      Some(""),
      None,
      testAppContext.nowF(),
      "test@example.com",
      "",
      "",
      "",
      "WAITING",
      emailType = "HelloEmail",
      Nil
    )

    assertEquals(govNotifyEmailSender.useGovNotify(helloTypeEmail).unsafeRunSync(), Right(true))

    // The main event..
    
    // fakeNotifyWrapper returns a template with all these personalisations.
    // fakeMongoWrapper returns the case object, parent case object, config
    // fakeNotifyWrapper's generateTemplatePreview and sendEmail stringifies this content.

    val expectedResponse = Sent(newText=Some("new text"), newHtml=Some(s"""Expanded Parameters:
case:boolField:lower:bool:not=no
case:dateOfBirth:plusYears18:beforeNow=no
case:name=phillip
config:rt_customer_host=https://rt.example.com
parent:details.age:minusN18:gt10=yes"""))

    assertEquals(govNotifyEmailSender.sendMessage(helloTypeEmail).unsafeRunSync(), expectedResponse)

  }

  test("ensure a failing end-to-end scenario returns WAITING status so app will try again later") {

    val helloTypeEmail = new Email(
      "id1",
      Some(""),
      None,
      testAppContext.nowF(),
      "fail@example.com",     /* this clause triggers our mock emailsender to return an error. */
      "",
      "",
      "",
      "WAITING",
      emailType = "HelloEmail",
      Nil
    )

    assertEquals(govNotifyEmailSender.sendMessage(helloTypeEmail).unsafeRunSync(), Waiting)
  }
}
