package uk.gov.homeoffice.rtemailer

import munit.CatsEffectSuite
import java.util.Optional
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.commons.MongoDBList

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

    val testObj = new MongoDBObject(MongoDBObject(
      "string" -> "abc",
      "int" -> 123,
      "list" -> MongoDBList(List("x", "y", "z") :_*),
      "outer" -> MongoDBObject("inner" -> "inside"),
      "dotted.doesn't.work" -> "6",
      "bool" -> true
    ))

    assertEquals(extractDBField(testObj, "string"), Some(TString("abc")))
    assertEquals(extractDBField(testObj, "int"), Some(TString("123")))
    assertEquals(extractDBField(testObj, "list"), Some(TList(List("x", "y", "z"))))
    assertEquals(extractDBField(testObj, "outer.inner"), Some(TString("inside")))
    assertEquals(extractDBField(testObj, "dotted.doesn't.work"), None)
    assertEquals(extractDBField(testObj, "bool"), Some(TString("yes")))
    assertEquals(extractDBField(testObj, "missing"), None)
  }

}

