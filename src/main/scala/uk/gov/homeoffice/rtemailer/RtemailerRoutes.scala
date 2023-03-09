package uk.gov.homeoffice.rtemailer

import cats.effect.Sync
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import org.http4s.circe._

object RtemailerRoutes {

  implicit val statusEncoder: Encoder[AppStatus] = deriveEncoder[AppStatus]

  def allRoutes[F[_]: Sync](): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / "status" => Ok(Globals.status.asJson)
    }
  }

}
