package uk.gov.homeoffice.rtemailer.model

case class GovNotifyError(
  message :String,
  transient: Boolean = true, // A transient error is like a network blip. Trying again could fix it.
  personalisationField :Option[String] = None
)
