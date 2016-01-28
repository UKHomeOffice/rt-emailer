package cjp.emailer

import caseworkerdomain.lock.{ProcessLockRepository, ProcessLocking}
import domain.core.email.{Email, EmailRepository}
import domain.core.email.EmailStatus._
import grizzled.slf4j.Logging
import org.apache.commons.mail.EmailException

class Emailer(emailRepository: EmailRepository, emailSender: EmailSender, sender: EmailAddress, pollingFrequency: Int, override val processLockRepository: ProcessLockRepository) extends ProcessLocking with Logging {
  private val emailType = "WAITING_CUSTOMER_EMAILS"

  def sendEmails() = try {
    val emailsToSend = emailRepository.findByStatus(STATUS_WAITING)

    emailsToSend.foreach(sendEmail)
  } catch {
    case e: Exception => logger.error(e.getMessage)
  }

  def sendEmail(email: Email) {
    try {
      logger.info(s"Sending email to ${email.recipient}")
      emailSender.sendMessage(sender = sender, recipient = email.recipient, ccList = email.cc, subject = email.subject, message = email.text, html = Some(email.html))
      logger.info("Marking email as sent")
      emailRepository.updateStatus(email.emailId, STATUS_SENT)
    } catch {
      case e: EmailException =>
        logger.error(e.getMessage)
        emailRepository.updateStatus(email.emailId, STATUS_EMAIL_ADDRESS_ERROR)
      case e: Exception =>
        logger.error(e.getMessage)
    }
  }

  def start() = while (true) {
    withLock(emailType){sendEmails()}
    logger.info("Polling for new emails")
    Thread.sleep(pollingFrequency * 1000)
  }
}