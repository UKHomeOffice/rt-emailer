package uk.gov.homeoffice.rtemailer

import cats.effect.IO
import org.http4s._
import org.http4s.implicits._
import munit.CatsEffectSuite
import org.http4s._
import io.circe.parser._

class RoutesSpec extends CatsEffectSuite {

  val routes = RtemailerRoutes.allRoutes[IO]()
  val req = Request[IO](Method.GET, uri"/status")

  test("/status endpoint returns json with health information") {
    routes.run(req).value.unsafeRunSync() match {
      case Some(response) =>
        val responseText = new String(response.body.compile.toVector.unsafeRunSync().toArray, "UTF-8")
        parse(responseText) match {
          case Left(parseErr) => fail(s"Unable to parse response from /status as json: $parseErr")
          case Right(obj) =>
            obj.hcursor.downField("appName").as[String] match {
              case Left(_) => fail(s"Unable to appName in /status")
              case Right(appName) =>
                println(s"got app name: $appName")
                assert(appName == "rt-emailer")
            }
        }
        assert(response.status.code == 200)
      case None => fail(s"Got 404 in test: ${req}")
    }
  }

}
