package uk.gov.homeoffice.rtemailer

import cats.effect.Sync
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import io.circe.syntax._
import org.http4s.circe._
import uk.gov.homeoffice.rtemailer.model.AppStatus

object RtemailerRoutes {

  def allRoutes[F[_]: Sync](): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._

    HttpRoutes.of[F] {
      // There is a function on the AppStatus called overallStatus() which could
      // be used to change the OK here into an InternalServerError if we wanted
      // to signal to Kubernetes the app isn't working. However, since we reconnect
      // to Mongo, GovNotify and Amazon SES after failures, I'd rather sit idle
      // when errors occur than force a reboot until I have more experience of how the application
      // behaves in production.
      case GET -> Root / "status" => Ok(AppStatus.getAppStatus().flatten().asJson)
    }
  }

}
