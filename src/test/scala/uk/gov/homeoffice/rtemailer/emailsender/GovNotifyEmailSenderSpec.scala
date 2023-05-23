package uk.gov.homeoffice.rtemailer.emailsender

import munit.CatsEffectSuite
import cats.effect._
import org.joda.time.DateTime
import com.typesafe.config.ConfigFactory
import uk.gov.homeoffice.rtemailer.model.AppContext
import org.bson.types.ObjectId

import com.mongodb.casbah.commons.MongoDBObject
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import uk.gov.homeoffice.rtemailer.model._
import uk.gov.homeoffice.rtemailer.govnotify._
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
        apiKey2 = ""
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

    override def getAllTemplates() :IO[Either[GovNotifyError, List[TemplateWC]]] = {
      IO.delay(Right(List(

        // This is a realistic example which is explored in the final test
        // which runs through the complete scenario, including parents, config
        // and more.
        TemplateWC(new Template(s"""{
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
        }"""), null),
        TemplateWC(new Template(s"""{
          "id":"${java.util.UUID.randomUUID()}",
          "name":"InfoEmail",
          "type":"email",
          "created_at":"2000-01-01T00:00:00.000Z",
          "updated_at":"2000-01-01T00:00:00.000Z",
          "version":3,
          "body":"hello customer",
          "subject":"hello",
          "personalisation":{"name":"string"}
        }"""), null),
        TemplateWC(new Template(s"""{
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
      )))
    }

    override def generateTemplatePreview(template :TemplateWC, personalisations :Map[String, String]) :IO[Either[GovNotifyError, TemplatePreview]] = {

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

    override def sendEmail(email :Email, template: TemplateWC, allPersonalisations :Map[String, String]) :IO[Either[GovNotifyError, SendEmailResponse]] = {

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

  val simpleCaseId = new ObjectId().toHexString()
  val parentCaseId = new ObjectId().toHexString()
  val defaultCaseId = new ObjectId().toHexString()

  val fakeMongoWrapper = new GovNotifyMongoWrapper()(testAppContext) {
    override def caseObjectFromEmail(email :Email) :IO[Either[GovNotifyError, Option[MongoDBObject]]] = {
      email.caseId match {
        case None => IO.delay(Right(None))
        case Some(x) if x == parentCaseId =>
          // part of the end-to-end scenario
          IO.delay(Right(Some(new MongoDBObject(MongoDBObject(
            "name" -> "phillip",
            "dateOfBirth" -> DateTime.parse("2020-01-02T11:11:11Z"),
            "boolField" -> "True", // test demostrates how to smooth a string like this into "yes" manually
            "latestApplication" -> MongoDBObject("parentRegisteredTravellerNumber" -> "RT123")
          )))))
        case Some(x) if x == simpleCaseId => IO.delay(Right(Some(new MongoDBObject(MongoDBObject(
          "name" -> "phillip",
          "details" -> MongoDBObject("age" -> 17)
        )))))
        case Some(_) =>
          IO.delay(Right(Some(new MongoDBObject(MongoDBObject(
            "name" -> "phillip",
            "details" -> MongoDBObject(
              "age" -> "17",
              "bool" -> true,
              "date" -> DateTime.parse("2023-02-01T13:44:55")
            )
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

    val testEmail = new Email(new ObjectId().toHexString, None, None, testAppContext.nowF(), "test@example.com", "", "", "", "WAITING", emailType = "HelloEmail", Nil)
    val unknownType = new Email(new ObjectId().toHexString, None, None, testAppContext.nowF(), "test@example.com", "", "", "", "WAITING", emailType = "UnknownType", Nil)

    assertEquals(govNotifyEmailSender.useGovNotify(testEmail).unsafeRunSync(), Right(true))
    assertEquals(govNotifyEmailSender.useGovNotify(unknownType).unsafeRunSync(), Right(false))
  }

  test("use legacy approach if email:personaliations encountered in template but not in email table (backwards-compat feature)") {
    val emailWithoutPersonalisations = new Email(new ObjectId().toHexString, None, None, testAppContext.nowF(), "test@example.com", "", "", "", "WAITING", emailType = "TemplateWithPersonalisations", Nil, personalisations=None)
    val emailWithPersonalisations = new Email(new ObjectId().toHexString, None, None, testAppContext.nowF(), "test@example.com", "", "", "", "WAITING", emailType = "TemplateWithPersonalisations", Nil, personalisations=Some(MongoDBObject("x" -> "banana")))

    assertEquals(govNotifyEmailSender.useGovNotify(emailWithoutPersonalisations).unsafeRunSync(), Right(false))
    assertEquals(govNotifyEmailSender.useGovNotify(emailWithPersonalisations).unsafeRunSync(), Right(true))
  }

  test("extracting parameters from case objects work") {
    val email = new Email(new ObjectId().toHexString, Some(simpleCaseId), None, testAppContext.nowF(), "test@example.com", "", "", "", "WAITING", emailType = "DefaultTemplate", Nil)

    val testPersonalisations = List("case:name", "case:details.age")

    val result = govNotifyEmailSender.buildCasePersonalisations(testPersonalisations, email, "test-template").unsafeRunSync()
    result match {
      case Left(err) =>
        fail(s"unexpected failure in test: $err")
      case Right((personalisationMap, caseObj)) =>
        assertEquals(
          personalisationMap,
          Map(
            "case:name" -> "phillip",
            "case:details.age" -> "17"
          )
        )
        assert(caseObj.isDefined)
    }
  }

  test("build process can proceed gracefully without case reference") {
    val email = new Email(new ObjectId().toHexString, None, None, testAppContext.nowF(), "test@example.com", "", "", "", "WAITING", emailType = "XXX", Nil)
    val testPersonalisations = List("case:name", "case:details.age")

    val caseResult = govNotifyEmailSender.buildCasePersonalisations(testPersonalisations, email, "test-template").unsafeRunSync()
    assertEquals(caseResult, Right((Map.empty, None)) :Either[GovNotifyError, (Map[String, String], Option[MongoDBObject])])

    val parentResult = govNotifyEmailSender.buildParentPersonalisations(testPersonalisations, None, "test-template").unsafeRunSync()
    assertEquals(parentResult, Right(Map.empty) :Either[GovNotifyError, Map[String, String]])
  }

  test("extracting parameters from a parent object works (in isolation)") {
    val testCase = new MongoDBObject(MongoDBObject(
      "latestApplication" -> MongoDBObject("parentRegisteredTravellerNumber" -> "RTAA122"),
      "name" -> "phillip",
      "details" -> MongoDBObject("age" -> 17)
    ))

    val testPersonalisations = List("case:name", "parent:name", "parent:details.age")

    val result = govNotifyEmailSender.buildParentPersonalisations(testPersonalisations, Some(testCase), "test-template")
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
    val email = new Email(new ObjectId().toHexString, Some(defaultCaseId), None, testAppContext.nowF(), "test@example.com", "", "", "", "WAITING", emailType = "TemplateWithPersonalisations", Nil, personalisations=Some(MongoDBObject("x" -> "banana")))

    val testPersonalisations = List("case:givenYes", "case:total.sum")

    val result = govNotifyEmailSender.buildCasePersonalisations(testPersonalisations, email, "test-template-empty").unsafeRunSync()
    result match {
      case Left(err) =>
        fail(s"unexpected failure in test: $err")
      case Right((personalisationMap, _)) =>
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
    val email = new Email(new ObjectId().toHexString, Some(defaultCaseId), None, testAppContext.nowF(), "test@example.com", "", "", "", "WAITING", emailType = "TemplateWithPersonalisations", Nil, personalisations=Some(MongoDBObject("time" -> DateTime.parse("2020-03-01T12:33:44Z"))))

    val testPersonalisations = List("email:personalisations.time:date")

    val result = govNotifyEmailSender.buildEmailPersonalisations(testPersonalisations, email, "test-template-empty")
    result match {
      case Left(err) =>
        fail(s"unexpected failure in test: $err")
      case Right(personalisationMap) =>
        assertEquals(personalisationMap, Map("email:personalisations.time:date" -> "01 March 2020"))
    }
  }

  test("when the field is a boolean it is formatted as yes/no") {
    val email = new Email(new ObjectId().toHexString, Some(defaultCaseId), None, testAppContext.nowF(), "test@example.com", "", "", "", "WAITING", emailType = "TemplateWithPersonalisations", Nil, personalisations=Some(MongoDBObject("bool" -> true)))

    val testPersonalisations = List("email:personalisations.bool")

    val result = govNotifyEmailSender.buildEmailPersonalisations(testPersonalisations, email, "test-template-empty")
    result match {
      case Left(err) =>
        fail(s"unexpected failure in test: $err")
      case Right(personalisationMap) =>
        assertEquals(
          personalisationMap,
          Map("email:personalisations.bool" -> "yes")
        )
    }
  }

  test("full good end-to-end scenario") {

    val helloTypeEmail = new Email(
      new ObjectId().toHexString(),
      Some(parentCaseId),
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
      new ObjectId().toHexString(),
      Some(new ObjectId().toHexString()),
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

