package uk.gov.homeoffice.rtemailer.emailsender

import cats.data.EitherT
import cats.effect._
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import com.typesafe.scalalogging.StrictLogging
import uk.gov.homeoffice.mongo.casbah.MongoDBObject
import scala.collection.JavaConverters._
import scala.util.Try
import scala.concurrent.duration.Duration

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

  // If GovNotify explodes, don't supress and fallback to legacy SMTP solution.
  // Queue emails until a developer investigates.

  // As part of EVW release, since some email info cannot be recalled from case:
  def useGovNotify(email: Email) :IO[Either[GovNotifyError, Boolean]] = {
    getTemplate(email).map {
      case Right(Some(template)) =>
        val usesEmailPersonalisation = getPersonalisationsRequired(template).exists(_.startsWith("email:personalisations"))
        val hasEmailPersonalisation = email.personalisations.isDefined

        (usesEmailPersonalisation, hasEmailPersonalisation) match {
          case (true, false) => Right(false) /* if the email uses email:personalisation fields but our email has none, use legacy sending method. (only happens with resend behaviour) */
          case _ => Right(true)              /* otherwise mere presence of template means use govNotify */
        }

      // propogate gov notify errors, don't let blips cause the system to send legacy emails
      case Left(govNotifyError) => Left(govNotifyError)

      // No template, then don't use govNotify
      case Right(None) => Right(false)
    }
  }

  // if a config value is not found, we just return empty string, we do not throw an error.
  def buildConfigPersonalisations(personalisationsRequired :List[String], templateName :String) :Either[GovNotifyError, Map[String, String]] = {
    def resolveConfigValue(configName :String) :Either[GovNotifyError, String] = {
      appContext.config.hasPath(s"govNotify.staticPersonalisations.$configName") match {
        case true => Try(appContext.config.getString(s"govNotify.staticPersonalisations.$configName"))
            .toEither
            .left.map(exc => GovNotifyError(s"Config error govNotify.staticPersonalisations.$configName: ${exc.getMessage}")) case false => logger.warn(s"gov notify personalisation warning: missing field: $configName (template name: $templateName)")
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
          logger.warn(s"gov notify personalisation warning: $personalisationRequired. error: lookup doesn't result in string. missing date/csvList call? (templateName: $templateName)")
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

  def buildCasePersonalisations(personalisationsRequired :List[String], email :Email, templateName :String) :IO[Either[GovNotifyError, (Map[String, String], Option[MongoDBObject])]] = {
    appContext.database.caseObjectFromEmail(email).map {
      case Right(Some(caseObj)) =>
        buildObjectPersonalisations(personalisationsRequired, caseObj, templateName, "case").map { m => (m, Some(caseObj)) }
      case Right(None) =>
        Right((Map.empty, None))
      case Left(govNotifyError) => Left(govNotifyError)
    }
  }

  def buildEmailPersonalisations(personalisationsRequired :List[String], email :Email, templateName :String) :Either[GovNotifyError, Map[String, String]] = {
    buildObjectPersonalisations(personalisationsRequired, email.toDBObject.mongoDBObject, templateName, "email")
  }

  def buildParentPersonalisations(personalisationsRequired :List[String], maybeCaseObj :Option[MongoDBObject], templateName :String) :IO[Either[GovNotifyError, Map[String, String]]] = {
    /* save us some effort by skipping parent lookup if there are no "parent:" calls required. (This also makes logging less noisy) */
    personalisationsRequired.exists(_.startsWith("parent:")) match {
      case true =>
        maybeCaseObj match {
          case Some(caseObj) =>
            appContext.database.parentObjectFromCaseObject(caseObj)
              .map {
                case Right(Some(parentCaseObj)) => buildObjectPersonalisations(personalisationsRequired, parentCaseObj, templateName, "parent")
                case Right(None) => Right(Map.empty)
                case Left(err) =>
                  logger.warn(s"gov notify personalisation warning fetching parent: ${err}")
                  Left(err)
              }
          case None => IO.delay(Right(Map.empty))
        }
      case false => IO.delay(Right(Map.empty))
    }
  }

  def getPersonalisationsRequired(template :TemplateWC) :List[String] = {
    val personalisationsRequired = template.getPersonalisation().asScalaOption.map(_.keySet.asScala.toList).getOrElse(List.empty)
    if (appContext.config.getBoolean("app.templateDebug")) {
      logger.info(s"Personalisatons Required for ${template.getName()} (${template.getId()}): $personalisationsRequired")
    }
    personalisationsRequired
  }

  def getTemplate(email :Email) :IO[Either[GovNotifyError, Option[TemplateWC]]] = {
    notifyClientWrapper.getAllTemplates().map { _.map { allTemplates =>
      allTemplates.find(_.getName() == email.emailType)
    }}
  }

  def sendMessage(email: Email) :IO[EmailSentResult] = {

    getTemplate(email).flatMap {
      case Right(Some(template)) =>
        val personalisationsRequired = getPersonalisationsRequired(template)

        val allPersonalisations = for {
          (casePersonalisations, caseObj) <- EitherT(buildCasePersonalisations(personalisationsRequired, email, template.getName()))
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
                    case Left(govNotifySendError) if govNotifySendError.transient && exhaustedRetries(email) =>
                      logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} via GovNotify. Error during send: $govNotifySendError. Exhausted Retries")
                      ExhaustedRetries
                    case Left(govNotifySendError) if govNotifySendError.transient =>
                      logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} via GovNotify. Error during send: $govNotifySendError")
                      Waiting
                    case Left(govNotifySendError) if !govNotifySendError.transient =>
                      logger.error(s"Cannot send email ${email.emailId} via GovNotify due to partial success/failure. To avoid spamming, delivery will not be retried. Error during send: $govNotifySendError")
                      PartialError(govNotifySendError.message)
                }
              case Left(govNotifyTemplateError) =>
                logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} via GovNotify. Error generating template preview: $govNotifyTemplateError")
                IO.delay(Waiting)
          }
        }
      case Right(None) =>
        logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} via GovNotify as no template is found")
        IO.delay(Waiting)
      case Left(err) =>
        logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} due to error fetching template: $err")
        IO.delay(Waiting)
    }
  }

  def exhaustedRetries(email :Email) :Boolean = {
    email.date.isBefore(appContext.nowF().minusHours(Duration(appContext.config.getString("govNotify.exhaustedTimeout")).toHours.toInt))
  }
}

