package uk.gov.homeoffice.rtemailer.model

case class GovNotifyError(
  message :String,
  /* A transient error is like a network blip. Trying again could fix it.
    This is the default behaviour. However if this is set to false it is
    important to respect it. One such example is when we send an email that
    has a lot of 'cc' emails listed. On failure we must never "try again"
    as it can spam everyone else in the cc list.
  */
  transient: Boolean = true,
  personalisationField :Option[String] = None
)
