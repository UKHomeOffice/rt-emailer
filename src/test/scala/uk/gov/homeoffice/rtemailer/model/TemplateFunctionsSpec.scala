package uk.gov.homeoffice.rtemailer.model

import munit.CatsEffectSuite
import org.joda.time.DateTime
import com.typesafe.config.ConfigFactory

class TemplateFunctionsSpec extends CatsEffectSuite {

  val templateFunctions = new TemplateFunctions()(new AppContext(
    nowF = () => DateTime.parse("2024-01-01T01:02:03"),
    ConfigFactory.empty,
    null
  ))

  def testFunc = templateFunctions.applyFunction _
  def testChain(input :String, raw :String) = templateFunctions.applyFunctions(input, raw.split(":").toList)

  test("right") {
    assertEquals(testFunc("hello", "right2"), Right("lo"))
    assertEquals(testFunc("hello", "right3"), Right("llo"))
  }

  test("minus days") {
    assertEquals(testFunc("01 January 2023", "minusDays2"), Right("30 December 2022"))
    assertEquals(testFunc("07 September 2023", "minusDays6"), Right("01 September 2023"))
  }

  test("minus weeks") {
    assertEquals(testFunc("01 January 2023", "minusWeeks1"), Right("25 December 2022"))
  }

  test("minus months") {
    assertEquals(testFunc("30 April 2023", "minusMonths2"), Right("28 February 2023"))
  }

  test("minus years") {
    assertEquals(testFunc("30 April 2023", "minusYears25"), Right("30 April 1998"))
  }

  test("plus days") {
    assertEquals(testFunc("30 December 2022", "plusDays2"), Right("01 January 2023"))
    assertEquals(testFunc("02 September 2023", "plusDays6"), Right("08 September 2023"))
  }

  test("plus weeks") {
    assertEquals(testFunc("01 January 2023", "plusWeeks1"), Right("08 January 2023"))
  }

  test("plus months") {
    assertEquals(testFunc("30 April 2023", "plusMonths2"), Right("30 June 2023"))
  }

  test("plus years") {
    assertEquals(testFunc("30 April 2023", "plusYears1"), Right("30 April 2024"))
  }

  test("beforeNow") {
    // At top of file we mock nowF to be 1st Jan 2024
    assertEquals(testFunc("31 December 2023", "beforeNow"), Right("yes"))
    assertEquals(testFunc("01 January 2024", "beforeNow"), Right("yes"))
    assertEquals(testFunc("02 January 2024", "beforeNow"), Right("no"))
  }

  test("chained date functions") {
    assertEquals(testFunc("01 January 2024", "beforeNow"), Right("yes"))
    assertEquals(testChain("01 January 2024", "beforeNow:not"), Right("no"))
    assertEquals(testChain("01 January 2024", "plusYears1:beforeNow:not"), Right("yes"))
  }

  test("greater than (gt)") {
    assertEquals(testFunc("12", "gt13"), Right("no"))
    assertEquals(testFunc("13", "gt13"), Right("no"))
    assertEquals(testFunc("14", "gt13"), Right("yes"))
    assertEquals(testChain("14", "gt13:not"), Right("no"))
    assertEquals(testFunc("5000", "gt7000"), Right("no"))
  }

  test("contains") {
    assertEquals(testFunc("help", "containshelp"), Right("yes"))
    assertEquals(testFunc("help", "containsel"), Right("yes"))
    assertEquals(testFunc("help", "containsHELP"), Right("no"))
    assertEquals(testFunc("help", "containsChipmonk"), Right("no"))
  }

  test("minusN") {
    assertEquals(testFunc("16", "minusN10"), Right("6"))
    assertEquals(testFunc("36", "minusN20"), Right("16"))
  }

  test("plusN") {
    assertEquals(testFunc("10", "plusN5"), Right("15"))
    assertEquals(testFunc("5", "plusN100"), Right("105"))
  }

  test("bool") {
    assertEquals(testFunc("true", "bool"), Right("yes"))
    assertEquals(testFunc("false", "bool"), Right("no"))
    assertEquals(testFunc("anythingelse", "bool"), Right("no"))
    assertEquals(testFunc("yes", "bool"), Right("no")) /* expected! */
  }

  test("lower") {
    assertEquals(testFunc("CraFty", "lower"), Right("crafty"))
  }

  test("upper") {
    assertEquals(testFunc("CraFty", "upper"), Right("CRAFTY"))
  }

  test("title") {
    assertEquals(testFunc("craFty", "title"), Right("Crafty"))
  }

  test("empty") {
    assertEquals(testFunc("craFty", "empty"), Right("no"))
    assertEquals(testFunc("", "empty"), Right("yes"))
  }

  test("not") {
    assertEquals(testFunc("yes", "not"), Right("no"))
    assertEquals(testFunc("no", "not"), Right("yes"))
    assertEquals(testFunc("anythingelse", "not"), Right("yes"))
  }

  test("pounds") {
    assertEquals(testFunc("5000", "pounds"), Right("50.00"))
    assertEquals(testFunc("1234", "pounds"), Right("12.34"))
  }

  test("trim") {
    assertEquals(testFunc("PHIL", "trim"), Right("PHIL"))
    assertEquals(testFunc("   PHIL   WAS ", "trim"), Right("PHIL   WAS"))
  }

  test("chained functions") {
    assertEquals(testChain("   PHIL TAY   ", "trim:right3:title"), Right("Tay"))
    assertEquals(testChain("   PHIL TAY   ", "trim:empty:not"), Right("yes"))
    assertEquals(testChain("      ", "trim:empty:not"), Right("no"))
    assertEquals(testChain("TRUE", "lower:bool:not:not"), Right("yes"))
  }

}

