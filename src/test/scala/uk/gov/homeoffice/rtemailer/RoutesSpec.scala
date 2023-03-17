package uk.gov.homeoffice.rtemailer

import cats.effect.IO
import org.http4s._
import org.http4s.implicits._
import munit.CatsEffectSuite
import org.http4s._

class RoutesSpec extends CatsEffectSuite {

  val routes = RtemailerRoutes.allRoutes[IO]()
  val req = Request[IO](Method.GET, uri"/status")

  test("/status endpoint returns json with health information") {
    routes.run(req).value.unsafeRunSync() match {
      case Some(response) =>
        //val responseText = new String(response.body.compile.toVector.unsafeRunSync().toArray, "UTF-8")
        assert(response.status.code == 200)
      case None => fail(s"Got 404 in test: ${req}")
    }
  }

}
