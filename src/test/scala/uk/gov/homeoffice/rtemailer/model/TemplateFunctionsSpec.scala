package uk.gov.homeoffice.rtemailer.model

import munit.CatsEffectSuite
import org.joda.time.DateTime
import com.typesafe.config.ConfigFactory

class TemplateFunctionsSpec extends CatsEffectSuite {

  val templateFunctions = new TemplateFunctions()(new AppContext(
    nowF = () => DateTime.parse("2024-01-01T01:02:03"),
    ConfigFactory.parseString("""
      app {
        updateTokenSecret = "bonjour mon ami"
      }
    """),
    null
  ))

  def testFunc = templateFunctions.applyFunction _
  def testChain(input :TemplateLookup, raw :String) = templateFunctions.applyFunctions(input, raw.split(":").toList)

  test("right") {
    assertEquals(testFunc(TString("hello"), "right2"), Right(TString("lo")))
    assertEquals(testFunc(TString("hello"), "right3"), Right(TString("llo")))
  }

  test("minus days") {
    assertEquals(testFunc(TDate(DateTime.parse("2023-01-01T00:00:00Z")), "minusDays2"), Right(TDate(DateTime.parse("2022-12-30T00:00:00Z"))))
    assertEquals(testFunc(TDate(DateTime.parse("2023-09-07T00:00:00Z")), "minusDays6"), Right(TDate(DateTime.parse("2023-09-01T00:00:00Z"))))
  }

  test("minus weeks") {
    assertEquals(testFunc(TDate(DateTime.parse("2023-01-01T00:00:00Z")), "minusWeeks1"), Right(TDate(DateTime.parse("2022-12-25T00:00:00Z"))))
  }

  test("minus months") {
    assertEquals(testFunc(TDate(DateTime.parse("2023-04-30T00:00:00Z")), "minusMonths2"), Right(TDate(DateTime.parse("2023-02-28T00:00:00Z"))))
  }

  test("minus years") {
    assertEquals(testFunc(TDate(DateTime.parse("2023-04-30T00:00:00Z")), "minusYears25"), Right(TDate(DateTime.parse("1998-04-30T00:00:00Z"))))
  }

  test("plus days") {
    assertEquals(testFunc(TDate(DateTime.parse("2022-12-30T00:00:00Z")), "plusDays2"), Right(TDate(DateTime.parse("2023-01-01T00:00:00Z"))))
    assertEquals(testFunc(TDate(DateTime.parse("2022-09-02T00:00:00Z")), "plusDays6"), Right(TDate(DateTime.parse("2022-09-08T00:00:00Z"))))
  }

  test("plus weeks") {
    assertEquals(testFunc(TDate(DateTime.parse("2023-01-01T00:00:00Z")), "plusWeeks1"), Right(TDate(DateTime.parse("2023-01-08T00:00:00Z"))))
  }

  test("plus months") {
    assertEquals(testFunc(TDate(DateTime.parse("2023-04-30T00:00:00Z")), "plusMonths2"), Right(TDate(DateTime.parse("2023-06-30T00:00:00Z"))))
  }

  test("plus years") {
    assertEquals(testFunc(TDate(DateTime.parse("2023-04-30T00:00:00Z")), "plusYears1"), Right(TDate(DateTime.parse("2024-04-30T00:00:00Z"))))
  }

  test("beforeNow") {
    // At top of file we mock nowF to be 1st Jan 2024
    assertEquals(testFunc(TDate(DateTime.parse("2023-12-31T00:00:00Z")), "beforeNow"), Right(TString("yes")))
    assertEquals(testFunc(TDate(DateTime.parse("2024-01-01T00:00:00Z")), "beforeNow"), Right(TString("yes")))
    assertEquals(testFunc(TDate(DateTime.parse("2024-02-01T00:00:00Z")), "beforeNow"), Right(TString("no")))
  }

  test("date and time") {
    assertEquals(testFunc(TDate(DateTime.parse("2024-04-01T00:00:00Z")), "date"), Right(TString("01 April 2024")))
    assertEquals(testFunc(TDate(DateTime.parse("2020-06-01T12:00:00Z")), "date"), Right(TString("01 June 2020")))
    assertEquals(testFunc(TDate(DateTime.parse("2024-01-01T12:30:20Z")), "time"), Right(TString("12:30")))
  }

  test("chained date functions") {
    assertEquals(testFunc(TDate(DateTime.parse("2024-01-01T00:00:00Z")), "beforeNow"), Right(TString("yes")))
    assertEquals(testChain(TDate(DateTime.parse("2024-01-01T00:00:00Z")), "beforeNow:not"), Right(TString("no")))
    assertEquals(testChain(TDate(DateTime.parse("2024-01-01T00:00:00Z")), "plusYears1:beforeNow:not"), Right(TString("yes")))
    assertEquals(testChain(TDate(DateTime.parse("2024-01-01T00:00:00Z")), "plusYears1:beforeNow:not"), Right(TString("yes")))
    assertEquals(testChain(TDate(DateTime.parse("2024-01-01T00:00:00Z")), "plusYears1:date"), Right(TString("01 January 2025")))
  }

  test("greater than (gt)") {
    assertEquals(testFunc(TString("12"), "gt13"), Right(TString("no")))
    assertEquals(testFunc(TString("13"), "gt13"), Right(TString("no")))
    assertEquals(testFunc(TString("14"), "gt13"), Right(TString("yes")))
    assertEquals(testChain(TString("14"), "gt13:not"), Right(TString("no")))
    assertEquals(testFunc(TString("5000"), "gt7000"), Right(TString("no")))
  }

  test("contains") {
    assertEquals(testFunc(TString("help"), "containshelp"), Right(TString("yes")))
    assertEquals(testFunc(TString("help"), "containsel"), Right(TString("yes")))
    assertEquals(testFunc(TString("help"), "containsHELP"), Right(TString("no")))
    assertEquals(testFunc(TString("help"), "containsChipmonk"), Right(TString("no")))
  }

  test("minusN") {
    assertEquals(testFunc(TString("16"), "minusN10"), Right(TString("6")))
    assertEquals(testFunc(TString("36"), "minusN20"), Right(TString("16")))
  }

  test("plusN") {
    assertEquals(testFunc(TString("10"), "plusN5"), Right(TString("15")))
    assertEquals(testFunc(TString("5"), "plusN100"), Right(TString("105")))
  }

  test("bool") {
    assertEquals(testFunc(TString("true"), "bool"), Right(TString("yes")))
    assertEquals(testFunc(TString("false"), "bool"), Right(TString("no")))
    assertEquals(testFunc(TString("anythingelse"), "bool"), Right(TString("no")))
    assertEquals(testFunc(TString("yes"), "bool"), Right(TString("no"))) /* expected! */
  }

  test("lower") {
    assertEquals(testFunc(TString("CraFty"), "lower"), Right(TString("crafty")))
  }

  test("upper") {
    assertEquals(testFunc(TString("CraFty"), "upper"), Right(TString("CRAFTY")))
  }

  test("title") {
    assertEquals(testFunc(TString("craFty"), "title"), Right(TString("Crafty")))
  }

  test("empty") {
    assertEquals(testFunc(TString("craFty"), "empty"), Right(TString("no")))
    assertEquals(testFunc(TString(""), "empty"), Right(TString("yes")))
  }

  test("not") {
    assertEquals(testFunc(TString("yes"), "not"), Right(TString("no")))
    assertEquals(testFunc(TString("no"), "not"), Right(TString("yes")))
    assertEquals(testFunc(TString("anythingelse"), "not"), Right(TString("yes")))
  }

  test("pounds") {
    assertEquals(testFunc(TString("5000"), "pounds"), Right(TString("50.00")))
    assertEquals(testFunc(TString("1234"), "pounds"), Right(TString("12.34")))
  }

  test("trim") {
    assertEquals(testFunc(TString("PHIL"), "trim"), Right(TString("PHIL")))
    assertEquals(testFunc(TString("   PHIL   WAS "), "trim"), Right(TString("PHIL   WAS")))
  }

  test("chained functions") {
    assertEquals(testChain(TString("   PHIL TAY   "), "trim:right3:title"), Right(TString("Tay")))
    assertEquals(testChain(TString("   PHIL TAY   "), "trim:empty:not"), Right(TString("yes")))
    assertEquals(testChain(TString("      "), "trim:empty:not"), Right(TString("no")))
    assertEquals(testChain(TString("TRUE"), "lower:bool:not:not"), Right(TString("yes")))
  }

  test("list stringification") {
    assertEquals(testFunc(TList(List("a", "b")), "csvList"), Right(TString("a,b")))
  }

  test("list contains") {
    assertEquals(testFunc(TList(List("acorn", "ball")), "containsball"), Right(TString("yes")))
    assertEquals(testFunc(TList(List("acorn", "ball")), "containsb"), Right(TString("no"))) /* full match expected */
    assertEquals(testFunc(TList(List("acorn", "ball")), "containsgirl"), Right(TString("no")))
  }

  test("list index") {
    assertEquals(testFunc(TList(List("a", "b", "c", "d", "e", "f")), "index4"), Right(TString("e")))
  }

  test("encrypted token test") {
    assertEquals(testChain(TString("abcdefg"), "accountToken"), Right(TString("6d38446695e7c87d8895a2eb388b44ea841bed93236034366c8cc7baef18c21c")))
  }
}

