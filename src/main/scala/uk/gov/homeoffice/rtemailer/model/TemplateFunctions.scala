package uk.gov.homeoffice.rtemailer.model

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import scala.annotation.tailrec
import scala.util.Try

sealed trait TemplateLookup { def stringValue() :Either[GovNotifyError, String] }
case class TString(string :String) extends TemplateLookup { override def stringValue() = Right(string) }
case class TList(list :List[String]) extends TemplateLookup { override def stringValue() = Left(GovNotifyError("Use csvList function to stringify TList object")) }
case class TDate(date :DateTime) extends TemplateLookup { override def stringValue() = Right(date.toString("dd MMMM yyyy")) }

class TemplateFunctions(implicit appContext: AppContext) {

  val dtf = DateTimeFormat.forPattern("dd MMMM yyyy")

  // We advertise to our users that we return blank/empty string if a function or field
  // does not work. for example "gtHello" would fail because "Hello" is not an Int.
  // However please return GovNotifyError instead of an empty string in a failure so
  // the calling function can attach the template name to a debug message and print it out.
  // Returning empty string on failure is done elsewhere.

  def applyStringFunction(value :TString, function :String) :Either[GovNotifyError, TemplateLookup] = {
    val Right = "right(\\d+)".r
    val MinusN = "minusN(\\d+)".r
    val PlusN = "plusN(\\d+)".r
    val GreaterThan = "gt(\\d+)".r
    val Contains = "contains(.*)".r

    Try(function match {
      case Right(count) => TString(value.string.takeRight(count.toInt))
      case GreaterThan(gtInt) => if (value.string.toInt > gtInt.toInt) TString("yes") else TString("no")
      case Contains(inStr) => if (value.string.contains(inStr)) TString("yes") else TString("no")
      case MinusN(int) =>
        val n = (value.string.toInt - int.toInt)
        TString(n.toString)
      case PlusN(int) =>
        val n = (value.string.toInt + int.toInt)
        TString(n.toString)
      case "bool" => if (value.string == "true") TString("yes") else TString("no")
      case "lower" => TString(value.string.toLowerCase)
      case "upper" => TString(value.string.toUpperCase)
      case "title" => TString(value.string.take(1).toUpperCase + value.string.drop(1).toLowerCase)
      case "empty" => if (value.string.isEmpty) TString("yes") else TString("no")
      case "not" => if (value.string == "yes") TString("no") else TString("yes")
      case "pounds" =>
        val d = (BigDecimal(value.string) / 100).setScale(2)
        TString(d.toString)
      case "trim" => TString(value.string.trim)
      case "accountToken" =>
        // evw membership number is only thing we ever use this function with: ((case:membershipNumber:accountToken))
        import com.roundeights.hasher.Implicits._
        TString(value.string.salt(appContext.config.getString("app.updateTokenSecret")).sha256.hex)
      case unknownFunction => throw new Exception(s"Invalid function name: $unknownFunction (for a string object)")
    }).toEither
      .left.map(exc => GovNotifyError(exc.getMessage))

  }

  def applyListFunction(value :TList, function :String) :Either[GovNotifyError, TemplateLookup] = {
    val Contains = "contains(.*)".r
    val Index = "index(\\d+)".r

    Try(function match {
      case "csvList" => TString(value.list.mkString(","))
      case Contains(text) => if (value.list.contains(text)) TString("yes") else TString("no")
      case Index(idx) => TString(value.list(idx.toInt))   // throws IndexOutOfBounds intentionally
      case unknownFunction => throw new Exception(s"Invalid function name: $unknownFunction (for a list object)")
    }).toEither
      .left.map(exc => GovNotifyError(exc.getMessage))
  }

  def applyDateFunction(value :TDate, function :String) :Either[GovNotifyError, TemplateLookup] = {
    val Minus = "minus(Days|Weeks|Months|Years)(\\d+)".r
    val Plus = "plus(Days|Weeks|Months|Years)(\\d+)".r

    def modify(f :DateTime => DateTime) = TDate(f(value.date))

    Try(function match {
      case "date" => TString(dtf.print(value.date))
      case "time" => TString(value.date.toString("HH:mm"))
      case Minus("Days", days) => modify(_.minusDays(days.toInt))
      case Minus("Weeks", weeks) => modify(_.minusWeeks(weeks.toInt))
      case Minus("Months", months) => modify(_.minusMonths(months.toInt))
      case Minus("Years", years) => modify(_.minusYears(years.toInt))
      case Plus("Days", days) => modify(_.plusDays(days.toInt))
      case Plus("Weeks", weeks) => modify(_.plusWeeks(weeks.toInt))
      case Plus("Months", months) => modify(_.plusMonths(months.toInt))
      case Plus("Years", years) => modify(_.plusYears(years.toInt))
      case "beforeNow" => if (value.date.isBefore(appContext.nowF())) TString("yes") else TString("no")
      case unknownFunction => throw new Exception(s"Invalid function name: $unknownFunction (for a date object)")
    }).toEither
      .left.map(exc => GovNotifyError(exc.getMessage))
  }

  def applyFunction(value :TemplateLookup, function :String) :Either[GovNotifyError, TemplateLookup] = value match {
    case v @ TString(_) => applyStringFunction(v, function)
    case v @ TList(_) => applyListFunction(v, function)
    case v @ TDate(_) => applyDateFunction(v, function)
  }

  @tailrec
  final def applyFunctions(value :TemplateLookup, functionList :List[String]) :Either[GovNotifyError, TemplateLookup] = functionList match {
    case Nil => Right(value)
    case headFunc :: tail =>
      applyFunction(value, headFunc) match {
        case Left(exc) =>
          Left(exc)
        case Right(newValue) =>
          applyFunctions(newValue, tail)
      }
  }

  def applyFunctionsStr(value :TemplateLookup, functionsList :List[String]) :Either[GovNotifyError, String] =
    for {
      funcResult <- applyFunctions(value, functionsList)
      stringifiedResult <- funcResult.stringValue()
    } yield { stringifiedResult }

}


