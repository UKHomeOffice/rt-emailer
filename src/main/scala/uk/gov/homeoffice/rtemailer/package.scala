package uk.gov.homeoffice.rtemailer

object Util {

  implicit class JavaOptionalOps[A](val underlying :java.util.Optional[A]) extends AnyVal {
    def asScalaOption() :Option[A] = if (underlying.isEmpty) None else Some(underlying.get())
  }

}
