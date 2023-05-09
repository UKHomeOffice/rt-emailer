package uk.gov.homeoffice.rtemailer.govnotify

import cats.effect._
import com.typesafe.scalalogging.StrictLogging
import uk.gov.service.notify.{NotificationClient, Template, TemplatePreview, SendEmailResponse}
import uk.gov.homeoffice.rtemailer.model._
import uk.gov.homeoffice.domain.core.email.Email
import scala.collection.JavaConverters._
import scala.util.Try

class GovNotifyClientWrapper(implicit appContext :AppContext) extends StrictLogging {
  import uk.gov.homeoffice.rtemailer.Util._

  lazy val notifyClient = new NotificationClient(appContext.config.getString("govNotify.apiKey"))

  def getAllTemplates() :IO[Either[GovNotifyError, List[Template]]] = {
    IO.blocking(Try(notifyClient.getAllTemplates("email").getTemplates().asScala.toList)
      .toEither
      .map { templates =>
        val templateNames = templates.map(_.getName()).mkString(",")
        logger.info(s"Templates returned from gov notify: $templateNames")
        templates
      }
      .left.map(exc => GovNotifyError(s"Error calling GovNotify.getAllTemplates: ${exc.getMessage}"))
    )
  }

  def generateTemplatePreview(template :Template, personalisations :Map[String, String]) :IO[Either[GovNotifyError, TemplatePreview]] = {
    IO.blocking(Try(notifyClient.generateTemplatePreview(
      template.getId().toString(),
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

  def sendEmail(email :Email, template :Template, allPersonalisations :Map[String, String]) :IO[Either[GovNotifyError, SendEmailResponse]] = {
    IO.blocking(Try(notifyClient.sendEmail(
      template.getId().toString(),
      email.recipient,
      allPersonalisations.asJavaMap(),
      email.emailId,
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

