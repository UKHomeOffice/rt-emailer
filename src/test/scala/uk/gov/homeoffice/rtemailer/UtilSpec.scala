package uk.gov.homeoffice.rtemailer

import munit.CatsEffectSuite
import java.util.Optional
import uk.gov.homeoffice.mongo.casbah.MongoDBObject
import uk.gov.homeoffice.mongo.casbah.MongoDBList

import uk.gov.homeoffice.rtemailer.model._

class UtilSpec extends CatsEffectSuite {
  import Util._

  test("java option to scala option works") {
    assertEquals(Optional.of[String]("hello").asScalaOption, Some("hello"))
    assertEquals(Optional.empty[String].asScalaOption, None)
  }

  test("java map to scala map works") {
    val jm = new java.util.HashMap[String, Object]()
    jm.put("hello", "test")

    assertEquals(Map("hello" -> "test").asJavaMap, jm)
  }

  test("pulling fields from a Mongo Object into our type system works") {

    val testObj = MongoDBObject(
      "string" -> "abc",
      "int" -> 123,
      "list" -> MongoDBList(List("x", "y", "z") :_*),
      "outer" -> MongoDBObject("inner" -> "inside"),
      "dotted.also.works" -> "6",
      "bool" -> true
    )

    assertEquals(extractDBField(testObj, "string"), Some(TString("abc")))
    assertEquals(extractDBField(testObj, "int"), Some(TString("123")))
    assertEquals(extractDBField(testObj, "list"), Some(TList(List("x", "y", "z"))))
    assertEquals(extractDBField(testObj, "outer.inner"), Some(TString("inside")))
    assertEquals(extractDBField(testObj, "dotted.also.works"), Some(TString("6")))
    assertEquals(extractDBField(testObj, "bool"), Some(TString("yes")))
    assertEquals(extractDBField(testObj, "missing"), None)
  }

}

