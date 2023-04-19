package uk.gov.homeoffice.rtemailer

import cats.data.EitherT
import cats.effect._
import uk.gov.homeoffice.domain.core.email.Email
import uk.gov.homeoffice.domain.core.email.EmailStatus._
import com.typesafe.scalalogging.StrictLogging
import uk.gov.service.notify.{Template, NotificationClient}
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import scala.util.Try

/*
 * GovNotify feature.
 *
 * 1. Lookup govNotify.template table to see if email type matches the name of a template.
 * 2. If it does, useGovNotify returns true, otherwise false.
 *
 * 3. Get the template from GovNotify
 * 4. Get the case object from the email.
 * 5. For an under18, get the case's parent object.
 * 6. Fetch the personalisations fields from the template
 * 7. Resolve what the field should be
 * 8. Generate an HTML template from GovNotify template
 * 9. Send the email via GovNotify and return the HTML to be stored.
*/

object GovNotifyEmailSender extends StrictLogging {

  lazy val notifyClient = new NotificationClient(Globals.config.getString("govNotify.apiKey"))
  lazy val caseTable :String = Globals.config.getString("govNotify.caseTable")

  implicit class JavaOptionalOps[A](val underlying :java.util.Optional[A]) extends AnyVal {
    def asScalaOption() :Option[A] = if (underlying.isEmpty) None else Some(underlying.get())
  }

  def useGovNotify(email: Email) :Boolean = {
    val allTemplates :List[Template] = notifyClient.getAllTemplates("email").getTemplates().asScala.toList
    logger.info(s"Templates available from GovNotify: ${allTemplates.map(_.getName()).mkString(",")}")
    allTemplates.exists(_.getName() == email.emailType)
  }

  def optExtract[A](dbObj :MongoDBObject, fieldName :String, dateFormat :Option[String] = None)(implicit m :Manifest[A]) :Option[String] = {
    import com.mongodb.casbah.Imports._

    scala.util.Try(dbObj.getAs[A](fieldName).map {
      case d :org.joda.time.DateTime => d.toString(dateFormat.getOrElse("YYYY-MM-dd'T'HH:mm:ssZ"))
      case d :java.util.Date => new org.joda.time.DateTime(d).toString(dateFormat.getOrElse("YYYY-MM-dd'T'HH:mm:ssZ"))
      case l :List[_] => l.mkString("; ")
      case o :ObjectId => o.toHexString
      case true => "yes"
      case false => "no"
      case anyStringable => anyStringable.toString()
    }).toOption.flatten
  }

  // extract will return the first non empty field.
  // extract[String]("x","y","z") will return the y if field x does not exist or x is not of type A. z ignored.

  @tailrec
  final def extract[A](dbObj :MongoDBObject, fieldName :String*)(implicit m :Manifest[A]) :String = optExtract[A](dbObj, fieldName.head) match {
    case Some(str) => str
    case None if fieldName.tail.isEmpty => ""
    case None => extract[A](dbObj, fieldName.tail :_*)
  }

  def extractDate(dbObj :MongoDBObject, fieldName :String*) :String = optExtract[java.util.Date](dbObj, fieldName.head) match {
    case Some(str) => str
    case None => optExtract[DateTime](dbObj, fieldName.head) match {
      case Some(str) => str
      case None if fieldName.tail.isEmpty => ""
      case None => extractDate(dbObj, fieldName.tail :_*)
    }
  }


  def caseObjectFromEmail(email :Email) :IO[Either[GovNotifyError, Option[MongoDBObject]]] = {
    email.caseId match {
      case Some(caseId) => IO.blocking(Try(
          Globals.mongoDB(caseTable).findOne(MongoDBObject("_id" -> new ObjectId(caseId)))
        ).toEither
          .map(_.map(new MongoDBObject(_)))
          .left.map(exc => GovNotifyError(s"Database error looking up case from email: ${exc.getMessage()}"))
        )
      case None => IO.delay(Right(None))
    }
  }

  def parentObjectFromCase(caseObj :MongoDBObject) :IO[Either[GovNotifyError, Option[MongoDBObject]]] = {
    optExtract[String](caseObj, "latestApplication.parentRegisteredTravellerNumber") match {
      case Some(parentRT) => IO.blocking(Try(
          Globals.mongoDB(caseTable).findOne(MongoDBObject("registeredTravellerNumber" -> parentRT))
        ).toEither
          .map(_.map(new MongoDBObject(_)))
          .left.map(exc => GovNotifyError(s"Database error looking up parent case from case: ${exc.getMessage()}"))
        )

      case None => IO.delay(Right(None))
    }
  }

  // if a config value is not found, we just return empty string, we do not throw an error.
  def buildConfigPersonalisations(personalisationsRequired :List[String], templateName :String) :Either[GovNotifyError, Map[String, String]] = {
    def resolveConfigValue(configName :String) :Either[GovNotifyError, String] = {
      Globals.config.hasPath(s"govNotify.staticPersonalisations.$configName") match {
        case true => Try(Globals.config.getString(s"govNotify.staticPersonalisations.$configName"))
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
          case Right(value) => Right((configName, value))
          case Left(govNotifyError) => Left(govNotifyError.copy(personalisationField = Some(configName)))
        }
    }

    allPersonalisations.partition(_.isLeft) match {
      case (Nil, goodResults) => Right(goodResults.collect { case Right((k, v)) => (k, v) }.toMap)
      case (Left(firstError) :: _, _) => Left(firstError)
      case _ => Left(GovNotifyError("programmer error. Non left function returned true for .isLeft"))
    }
  }

  def buildCasePersonalisations(personalisationsRequired :List[String], caseObj :MongoDBObject, templateName :String, prefix :String = "case") :Either[GovNotifyError, Map[String, String]] = {

    val allResolvedPersonalisations = personalisationsRequired.filter(_.startsWith(s"$prefix:")).map { personalisationRequired =>

      val (_ :: fieldName :: functionList) = personalisationRequired.split(":").toList

      val fieldValueStr :String = optExtract[Any](caseObj, fieldName, Some("dd MMMM yyyy")) match {
        case Some(fieldValue) => fieldValue.toString
        case None =>
          logger.warn(s"gov notify personalisation warning: missing field: $personalisationRequired (templateName: $templateName)")
          ""
      }

      TemplateFunctions.applyFunctions(fieldValueStr, functionList) match {
        case Right(resolvedValue) =>
          //logger.info(s"personalisation required: $personalisationRequired. resolved value: $resolvedValue")
          Right((personalisationRequired, resolvedValue))
        case Left(govNotifyError) =>
          logger.warn(s"gov notify personalisation warning: $personalisationRequired. error: $govNotifyError (templateName: $templateName)")
          Left(govNotifyError.copy(personalisationField = Some(personalisationRequired)))
      }
    }

    allResolvedPersonalisations.partition(_.isLeft) match {
      case (Nil, goodResults) => Right(goodResults.collect { case Right((k, v)) => (k, v) }.toMap)
      case (Left(firstError) :: _, _) => Left(firstError)
      case _ => Left(GovNotifyError("programmer error. Non left function returned true for .isLeft"))
    }
  }

  def buildParentPersonalisations(personalisationsRequired :List[String], caseObj :MongoDBObject, templateName :String) :IO[Either[GovNotifyError, Map[String, String]]] = {
    parentObjectFromCase(caseObj)
      .map {
        case Right(Some(parentCaseObj)) => buildCasePersonalisations(personalisationsRequired, parentCaseObj, templateName, "parent")
        case Right(None) => Right(Map.empty)
        case Left(err) =>
          logger.warn(s"gov notify personalisation warning fetching parent: ${err}")
          Left(err)
      }
  }

  def getAllTemplates() :IO[Either[GovNotifyError, List[Template]]] = {
    IO.blocking(
      Try(notifyClient.getAllTemplates("email").getTemplates().asScala.toList)
        .toEither
        .left.map(exc => GovNotifyError(s"Error fetching all templates from gov notify: ${exc.getMessage}"))
    )
  }

  def getTemplate(email :Email) :IO[Either[GovNotifyError, Option[Template]]] = {
    getAllTemplates().map { _.map { allTemplates =>
        logger.info(s"Templates available from GovNotify: ${allTemplates.map(_.getName()).mkString(",")}")
        allTemplates.find(_.getName() == email.emailType).map { template =>
            logger.info(s"Email type linked to GovNotify Template: ${template.getName()}, (templateId=${template.getId()})")
            template
        }
    }}
  }

  def getPersonalisationsRequired(template :Template) :List[String] = {
    val personalisationsRequired = template.getPersonalisation().asScalaOption.map(_.keySet.asScala.toList).getOrElse(List.empty)
    logger.info(s"Personalisatons Required for ${template.getName()} (${template.getId()}): $personalisationsRequired")
    personalisationsRequired
  }

  def sendMessage(email: Email) :IO[EmailSentResult] = {

    getTemplate(email).flatMap {
      case Right(Some(template)) =>
        val personalisationsRequired = getPersonalisationsRequired(template)
        caseObjectFromEmail(email).flatMap {
          case Right(Some(caseObj)) =>

            val allPersonalisations = for {
              casePersonalisations <- EitherT(IO.delay(buildCasePersonalisations(personalisationsRequired, caseObj, template.getName())))
              parentPersonalisations <- EitherT(buildParentPersonalisations(personalisationsRequired, caseObj, template.getName()))
              configPersonalisations <- EitherT(IO.delay(buildConfigPersonalisations(personalisationsRequired, template.getName())))
            } yield {

              val scalaMap = casePersonalisations ++ parentPersonalisations ++ configPersonalisations
              val javaMap = new java.util.HashMap[String, Object]()
              scalaMap.foreach { case (k, v) => javaMap.put(k, v) }
              javaMap
            }

            allPersonalisations.value.flatMap {
              case Left(err) =>
                logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} due to error: $err")
                IO.delay(Waiting)
              case Right(javaMap) =>
                IO.blocking(Try(notifyClient.generateTemplatePreview(
                  template.getId().toString(),
                  javaMap
                )).toEither).flatMap {
                  case Right(templatePreview) =>
                    IO.blocking(Try(notifyClient.sendEmail(
                      template.getId().toString(),
                      email.recipient,
                      javaMap,
                      email.emailId,
                    )).toEither).map {
                        case Right(response) =>
                          val govNotifyRef = response.getReference().asScalaOption.getOrElse("")
                          logger.info(s"Email sent via Gov Notify. Notification Id: ${response.getNotificationId()}, gov notify reference: ${govNotifyRef}, email table id: ${email.emailId}, template: ${response.getTemplateId()}, template version: ${response.getTemplateVersion()}")
                          Sent(newText = Some(templatePreview.getBody()), newHtml = templatePreview.getHtml().asScalaOption)
                        case Left(sendExc) =>
                          logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} via GovNotify. Error during send: ${sendExc.getMessage()}")
                          Waiting
                    }
                  case Left(templateExc) =>
                    logger.error(s"Cannot send email ${email.emailId} to ${email.recipient} via GovNotify. Error generating template preview: ${templateExc.getMessage()}")
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

case class GovNotifyError(message :String, transient: Boolean = true, personalisationField :Option[String] = None)

object TemplateFunctions {

  type TemplateFunction = String => String
  val functions = Map[String, TemplateFunction](
    "right4" -> { _.takeRight(4) },
    "bool" -> { x => if (x == "true") "yes" else "no" },
    "lower" -> { _.toLowerCase },
    "upper" -> { _.toUpperCase },
    "title" -> { x => x.take(1).toUpperCase + x.drop(1).toLowerCase },
    "empty" -> { x => if (x.isEmpty) "yes" else "no" },
    "not" -> { x => if (x == "yes") "no" else "yes" },
    "pounds" -> { t => Try((BigDecimal(t) / 100).setScale(2).toString).toOption.getOrElse("??.??") },
    "plus6months" -> { x =>
      val dtf = DateTimeFormat.forPattern("dd MMMM yyyy")
      /* warning, can thrown an exception */
      dtf.parseDateTime(x).plusMonths(6).minusDays(1).toString("dd MMMM yyyy")
    },
    "trim" -> { _.trim }
  )

  @tailrec
  def applyFunctions(input :String, functionList :List[String]) :Either[GovNotifyError, String] = functionList match {
    case Nil => Right(input)
    case head :: more => functions.get(head) match {
      case Some(func) =>
        Try(func(input)).toEither match {
          case Left(exc) => Left(GovNotifyError(s"template function error ($head on $input): ${exc.getMessage}"))
          case Right(modValue) => applyFunctions(modValue, more)
        }
      case None => Left(GovNotifyError(s"Bad function name: $head"))
    }
  }
}

