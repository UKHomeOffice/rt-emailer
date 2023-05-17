package uk.gov.homeoffice.rtemailer.govnotify

import cats.effect._
import cats.implicits._
import cats.data.EitherT
import com.typesafe.scalalogging.StrictLogging
import uk.gov.service.notify.{NotificationClient, Template, TemplatePreview, SendEmailResponse}
import uk.gov.homeoffice.rtemailer.model._
import uk.gov.homeoffice.domain.core.email.Email
import scala.collection.JavaConverters._
import scala.util.Try

case class TemplateWC(template :Template, client :NotificationClient) {
  def getPersonalisation() :java.util.Optional[java.util.Map[String,Object]] = template.getPersonalisation()
  def getName() :String = template.getName()
  def getId() :java.util.UUID = template.getId()
}

class GovNotifyClientWrapper(implicit appContext :AppContext) extends StrictLogging {
  import uk.gov.homeoffice.rtemailer.Util._

  val notifyClient1 = new NotificationClient(appContext.config.getString("govNotify.apiKey"))

  val notifyClient2 :Option[NotificationClient] = appContext.config.getString("govNotify.apiKey2") match {
    case "" => None
    case key2 => Some(new NotificationClient(key2))
  }

  def getAllTemplates() :IO[Either[GovNotifyError, List[TemplateWC]]] = {
    val client1Templates = getAllClientTemplates(notifyClient1)

    val client2Templates = notifyClient2 match {
      case None => IO.delay(Right(List.empty))
      case Some(client2) => getAllClientTemplates(client2)
    }

    (for {
      client1TemplatesList <- EitherT(client1Templates)
      client2TemplatesList <- EitherT(client2Templates)
    } yield { client1TemplatesList ++ client2TemplatesList }).value
  }

  private def getAllClientTemplates(client :NotificationClient) :IO[Either[GovNotifyError, List[TemplateWC]]] = {
    IO.blocking(Try(client.getAllTemplates("email").getTemplates().asScala.toList)
      .toEither
      .map { templates =>
        val templateNames = templates.map(_.getName()).mkString(",")
        logger.info(s"Templates returned from gov notify: $templateNames")
        // We attach the client to the template to support working with
        // templates with multiple clients, as is the scenario with RT/GE.
        templates.map { tmp => TemplateWC(tmp, client) }
      }
      .left.map(exc => GovNotifyError(s"Error calling GovNotify.getAllTemplates: ${exc.getMessage}"))
    )
  }

  def generateTemplatePreview(twc :TemplateWC, personalisations :Map[String, String]) :IO[Either[GovNotifyError, TemplatePreview]] = {
    IO.blocking(Try(twc.client.generateTemplatePreview(
      twc.template.getId().toString(),
      personalisations.asJavaMap(),
    )).toEither match {
      case Left(exc) =>
        appContext.updateAppStatus(_.recordGovNotifyError(s"Error calling GovNotify.generateTemplatePreview: ${exc.getMessage}"))
        Left(GovNotifyError(s"Error calling GovNotify.generateTemplatePreview: ${exc.getMessage}"))
      case Right(templatePreview) =>
        appContext.updateAppStatus(_.markGovNotifyOk)
        Right(templatePreview)
      }
    )
  }

  def sendEmail(email :Email, twc :TemplateWC, allPersonalisations :Map[String, String]) :IO[Either[GovNotifyError, SendEmailResponse]] = {
    val allCopies = List(email.recipient) ++ email.cc
    val emailsSent :IO[List[Either[GovNotifyError, SendEmailResponse]]] = allCopies.zipWithIndex.map {
      case (recipient, 0) => sendOneEmail(recipient, email.emailId, twc, allPersonalisations)
      case (recipient, n) => sendOneEmail(recipient, email.emailId + s"[cc=$n]", twc, allPersonalisations)
    }.sequence

    emailsSent.map { list =>
      list.partition(_.isLeft) match {
        case (Nil, firstGoodResult :: _) => firstGoodResult
        case (errors, _) =>
          errors.foreach(err => logger.error(s"Error sending govNotify email: $err"))
          errors.head
      }
    }
  }

  def sendOneEmail(recipient :String, emailReference :String, twc :TemplateWC, allPersonalisations :Map[String, String]) :IO[Either[GovNotifyError, SendEmailResponse]] = {
    IO.blocking(Try(twc.client.sendEmail(
      twc.template.getId().toString(),
      recipient,
      allPersonalisations.asJavaMap(),
      emailReference,
    ))
      .toEither match {
        case Left(exc) =>
          appContext.updateAppStatus(_.recordGovNotifyError(s"Error calling GovNotify.sendEmail: ${exc.getMessage}"))
          Left(GovNotifyError(s"Error calling GovNotify.sendEmail: ${exc.getMessage}"))
        case Right(sendEmailResponse) =>
          appContext.updateAppStatus(_.markGovNotifyOk)
          Right(sendEmailResponse)
      }
    )
  }
}

