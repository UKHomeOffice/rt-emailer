package uk.gov.homeoffice.rtemailer.model

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import scala.annotation.tailrec
import scala.util.Try

class TemplateFunctions(implicit appContext: AppContext) {

  val dtf = DateTimeFormat.forPattern("dd MMMM yyyy")

  def applyFunction(value :String, function :String) :Either[GovNotifyError, String] = {

    val Right = "right(\\d+)".r
    val Minus = "minus(Days|Weeks|Months|Years)(\\d+)".r
    val Plus = "plus(Days|Weeks|Months|Years)(\\d+)".r
    val GreaterThan = "gt(\\d+)".r
    val Contains = "contains(.*)".r

    def valueAsDate() = dtf.parseDateTime(value)
    def modifyAsDate(dateModFunc :DateTime => DateTime) = dtf.print(dateModFunc(valueAsDate()))

    Try(function match {
      case Right(count) => value.takeRight(count.toInt)
      case Minus("Days", days) => modifyAsDate(_.minusDays(days.toInt))
      case Minus("Weeks", weeks) => modifyAsDate(_.minusWeeks(weeks.toInt))
      case Minus("Months", months) => modifyAsDate(_.minusMonths(months.toInt))
      case Minus("Years", years) => modifyAsDate(_.minusYears(years.toInt))
      case Plus("Days", days) => modifyAsDate(_.plusDays(days.toInt))
      case Plus("Weeks", weeks) => modifyAsDate(_.plusWeeks(weeks.toInt))
      case Plus("Months", months) => modifyAsDate(_.plusMonths(months.toInt))
      case Plus("Years", years) => modifyAsDate(_.plusYears(years.toInt))
      case "beforeNow" => if (valueAsDate().isBefore(appContext.nowF())) "yes" else "no"
      case GreaterThan(gtInt) => if (value.toInt > gtInt.toInt) "yes" else "no"
      case Contains(inStr) => if (value.contains(inStr)) "yes" else "no"
      case "bool" => if (value == "true") "yes" else "no"
      case "lower" => value.toLowerCase
      case "upper" => value.toUpperCase
      case "title" => value.take(1).toUpperCase + value.drop(1).toLowerCase
      case "empty" => if (value.isEmpty) "yes" else "no"
      case "not" => if (value == "yes") "no" else "yes"
      case "pounds" => (BigDecimal(value) / 100).setScale(2).toString
      case "trim" => value.trim
      case unknownFunction => throw new Exception(s"Invalid function name: $unknownFunction")
    }).toEither
      .left.map(exc => GovNotifyError(exc.getMessage))

  }

  @tailrec
  final def applyFunctions(value :String, functionList :List[String]) :Either[GovNotifyError, String] = functionList match {
    case Nil => Right(value)
    case headFunc :: tail =>
      applyFunction(value, headFunc) match {
        case Left(exc) =>
          Left(exc)
        case Right(newValue) =>
          applyFunctions(newValue, tail)
      }
  }

}


