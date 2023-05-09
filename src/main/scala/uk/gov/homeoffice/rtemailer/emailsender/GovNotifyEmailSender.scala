package uk.gov.homeoffice.rtemailer.emailsender

import cats.data.EitherT
import cats.effect._
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import com.typesafe.scalalogging.StrictLogging
import uk.gov.service.notify.Template
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import scala.collection.JavaConverters._
import scala.util.Try
import uk.gov.homeoffice.rtemailer.model._
import uk.gov.homeoffice.rtemailer.govnotify._

/*
 * GovNotify feature.
 *
 * 1. Query GovNotify to fetch all templates (from multiple accounts if supplied)
 * 2. Does "email type" from email table match GovNotify template name?
 * 3. If it does, useGovNotify returns true, otherwise false.
 *
 * 4. Get the template from GovNotify
 * 5. Get the case object from the email.
 * 6. Get the case's parent object.
 * 7. Fetch the personalisations fields from the template
 * 8. Resolve what each field should be
 * 9. Generate an HTML template from GovNotify template
 * 10. Send the email via GovNotify and return the HTML to be stored.
*/

class GovNotifyEmailSender(implicit appContext :AppContext) extends StrictLogging {
  import uk.gov.homeoffice.rtemailer.Util._

  lazy val notifyClientWrapper = new GovNotifyClientWrapper()
  lazy val mongoWrapper = new GovNotifyMongoWrapper()

  // If GovNotify explodes, don't supress and fallback to legacy SMTP solution.
  // Queue emails until a developer investigates
  def useGovNotify(email: Email) :IO[Either[GovNotifyError, Boolean]] = {
    getTemplate(email).map { _.map { _.isDefined }}
  }

  // if a config value is not found, we just return empty string, we do not throw an error.
  def buildConfigPersonalisations(personalisationsRequired :List[String], templateName :String) :Either[GovNotifyError, Map[String, String]] = {
    def resolveConfigValue(configName :String) :Either[GovNotifyError, String] = {
      appContext.config.hasPath(s"govNotify.staticPersonalisations.$configName") match {
        case true => Try(appContext.config.getString(s"govNotify.staticPersonalisations.$configName"))
            .toEither
            .left.map(exc => GovNotifyError(s"Config error govNotify.staticPersonalisations.$configName: ${exc.getMessage}"))
        case false =>
          logger.warn(s"gov notify personalisation warning: missing field: $configName (template name: $templateName)")
          Right("")
      }
    }

    val allPersonalisations :List[Either[GovNotifyError, (String, String)]] = personalisationsRequired
      .filter(_.startsWith("config:"))
      .map { personalisationRequired =>
        val (_ :: configName :: _) = personalisationRequired.split(":").toList
        resolveConfigValue(configName) match {
          case Right(value) => Right((personalisationRequired, value))
          case Left(govNotifyError) => Left(govNotifyError.copy(personalisationField = Some(configName)))
        }
    }

    allPersonalisations.partition(_.isLeft) match {
      case (Nil, goodResults) => Right(goodResults.collect { case Right((k, v)) => (k, v) }.toMap)
      case (Left(firstError) :: _, _) => Left(firstError)
      case _ => Left(GovNotifyError("programmer error. Non left function returned true for .isLeft"))
    }
  }

  // Given any mongoDB Object and list of personalisations, this function looks up the value
  // and returns a Map[String, String] of them. The individual personalisationsRequired strings
  // contain functions (i.e.  case:myField:lower:containsX:not)
  //                           |       |       |
  //                           |       |       |
  //                        source    field   functions

  def buildObjectPersonalisations(personalisationsRequired :List[String], obj :MongoDBObject, templateName :String, prefix :String) :Either[GovNotifyError, Map[String, String]] = {

    val allResolvedPersonalisations = personalisationsRequired.filter(_.startsWith(s"$prefix:")).map { personalisationRequired =>

      val (_ :: fieldName :: functionList) = personalisationRequired.split(":").toList

      val fieldValue :TemplateLookup = extractDBField(obj, fieldName) match {
        case Some(fieldValue) =>
          if (appContext.config.getBoolean("app.templateDebug")) {
            logger.info(s"gov notify type resolution: $fieldName: $fieldValue")
          }
          fieldValue
        case None =>
          logger.warn(s"gov notify personalisation warning: missing field: $personalisationRequired (templateName: $templateName)")
          TString("")
      }

      new TemplateFunctions().applyFunctions(fieldValue, functionList) match {
        case Right(TString(resolvedValue)) =>
          if (appContext.config.getBoolean("app.templateDebug")) {
            logger.info(s"personalisation required: $personalisationRequired. resolved value: $resolvedValue")
          }
          Right((personalisationRequired, resolvedValue))
        case Right(list) =>
          logger.warn(s"gov notify personalisation warning: $personalisationRequired. error: lookup doesn't result in string. missing csvList call? (templateName: $templateName)")
          Right((personalisationRequired, list.stringValue()))
        case Left(govNotifyError) =>
          logger.warn(s"gov notify personalisation warning: $personalisationRequired. error: $govNotifyError (templateName: $templateName)")
          Left(govNotifyError.copy(personalisationField = Some(personalisationRequired)))
      }
    }

    allResolvedPersonalisations.partition(_.isLeft) match {
      // force Any into String here.
      case (Nil, goodResults) => Right(goodResults.collect { case Right((k, v)) => (k, v.toString) }.toMap)
      case (Left(firstError) :: _, _) => Left(firstError)
      case _ => Left(GovNotifyError("programmer error. Non left function returned true for .isLeft"))
    }
  }

  // We can use the generic buildObjectPersonalisations to extract
  // personalisations from a case object, an email object or a parent object.

  def buildCasePersonalisations(personalisationsRequired :List[String], caseObj :MongoDBObject, templateName :String) :Either[GovNotifyError, Map[String, String]] =
    buildObjectPersonalisations(personalisationsRequired, caseObj, templateName, "case")

  def buildEmailPersonalisations(personalisationsRequired :List[String], email :Email, templateName :String) :Either[GovNotifyError, Map[String, String]] = {
    buildObjectPersonalisations(personalisationsRequired, new MongoDBObject(email.toDBObject), templateName, "email")
  }

  def buildParentPersonalisations(personalisationsRequired :List[String], caseObj :MongoDBObject, templateName :String) :IO[Either[GovNotifyError, Map[String, String]]] = {
    mongoWrapper.parentObjectFromCase(caseObj)
      .map {
        case Right(Some(parentCaseObj)) => buildObjectPersonalisations(personalisationsRequired, parentCaseObj, templateName, "parent")
        case Right(None) => Right(Map.empty)
        case Left(err) =>
          logger.warn(s"gov notify personalisation warning fetching parent: ${err}")
          Left(err)
      }
  }


  def getPersonalisationsRequired(template :Template) :List[String] = {
    val personalisationsRequired = template.getPersonalisation().asScalaOption.map(_.keySet.asScala.toList).getOrElse(List.empty)
    if (appContext.config.getBoolean("app.templateDebug")) {
      logger.info(s"Personalisatons Required for ${template.getName()} (${template.getId()}): $personalisationsRequired")
    }
    personalisationsRequired
  }

  def getTemplate(email :Email) :IO[Either[GovNotifyError, Option[Template]]] = {
    notifyClientWrapper.getAllTemplates().map { _.map { allTemplates =>
      allTemplates.find(_.getName() == email.emailType)
    }}
  }

  def sendMessage(email: Email) :IO[EmailSentResult] = {

    getTemplate(email).flatMap {
      case Right(Some(template)) =>
        val personalisationsRequired = getPersonalisationsRequired(template)
        mongoWrapper.caseObjectFromEmail(email).flatMap {
          case Right(Some(caseObj)) =>

            val allPersonalisations = for {
              casePersonalisations <- EitherT(IO.delay(buildCasePersonalisations(personalisationsRequired, caseObj, template.getName())))
              parentPersonalisations <- EitherT(buildParentPersonalisations(personalisationsRequired, caseObj, template.getName()))
              emailPersonalisations <- EitherT(IO.delay(buildEmailPersonalisations(personalisationsRequired, email, template.getName())))
              configPersonalisations <- EitherT(IO.delay(buildConfigPersonalisations(personalisationsRequired, template.getName())))
            } yield {
              casePersonalisations ++ parentPersonalisations ++ emailPersonalisations ++ configPersonalisations
            }

            allPersonalisations.value.flatMap {
              case Left(err) =>
                logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} due to error: $err")
                IO.delay(Waiting)
              case Right(allPersonalisations) =>
                if (appContext.config.getBoolean("app.templateDebug")) {
                  logger.info(s"Personalisations: ${allPersonalisations.mkString("\n")}")
                }
                notifyClientWrapper.generateTemplatePreview(
                  template,
                  allPersonalisations
                ).flatMap {
                  case Right(templatePreview) =>
                    notifyClientWrapper.sendEmail(
                      email,
                      template,
                      allPersonalisations
                    ).map {
                        case Right(response) =>
                          val govNotifyRef = response.getReference().asScalaOption.getOrElse("")
                          logger.info(s"Email sent via Gov Notify. Notification Id: ${response.getNotificationId()}, gov notify reference: ${govNotifyRef}, email table id: ${email.emailId}, template: ${response.getTemplateId()}, template version: ${response.getTemplateVersion()}")
                          Sent(newText = Some(templatePreview.getBody()), newHtml = templatePreview.getHtml().asScalaOption)
                        case Left(govNotifySendError) =>
                          logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} via GovNotify. Error during send: $govNotifySendError")
                          Waiting
                    }
                  case Left(govNotifyTemplateError) =>
                    logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} via GovNotify. Error generating template preview: $govNotifyTemplateError")
                    IO.delay(Waiting)
                }
            }
          case Right(None) =>
            logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} via GovNotify as caseId is invalid (and required for every email using GovNotify)")
            IO.delay(Waiting)
          case Left(err) =>
            logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} due to error: $err")
            IO.delay(Waiting)
        }
      case Right(None) =>
        logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} via GovNotify as no template is found")
        IO.delay(Waiting)
      case Left(err) =>
        logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} due to error fetching template: $err")
        IO.delay(Waiting)
    }
  }
}

class GovNotifyMongoWrapper(implicit appContext :AppContext) extends StrictLogging {
  import uk.gov.homeoffice.rtemailer.Util._

  lazy val caseTable :String = appContext.config.getString("govNotify.caseTable")

  def caseObjectFromEmail(email :Email) :IO[Either[GovNotifyError, Option[MongoDBObject]]] = {
    email.caseId match {
      case Some(caseId) => IO.blocking(Try(
          appContext.mongoDB(caseTable).findOne(MongoDBObject("_id" -> new ObjectId(caseId)))
        ).toEither match {
          case Left(exc) =>
            appContext.updateAppStatus(_.recordDatabaseError(exc.getMessage))
            Left(GovNotifyError(s"Database error looking up case from email: ${exc.getMessage()}"))
          case Right(maybeObj) =>
            appContext.updateAppStatus(_.markDatabaseOk)
            Right(maybeObj.map(new MongoDBObject(_)))
          }
        )
      case None => IO.delay(Right(None))
    }
  }

  def parentObjectFromCase(caseObj :MongoDBObject) :IO[Either[GovNotifyError, Option[MongoDBObject]]] = {
    extractDBField(caseObj, "latestApplication.parentRegisteredTravellerNumber") match {
      case Some(parentRT) => IO.blocking(Try(
          appContext.mongoDB(caseTable).findOne(MongoDBObject("registeredTravellerNumber" -> parentRT.stringValue()))
        ).toEither
          .map(_.map(new MongoDBObject(_)))
          .left.map(exc => GovNotifyError(s"Database error looking up parent case from case: ${exc.getMessage()}"))
        )

      case None => IO.delay(Right(None))
    }
  }
}
