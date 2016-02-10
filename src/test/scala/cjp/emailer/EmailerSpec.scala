package cjp.emailer

import caseworkerdomain.lock.ProcessLockRepository
import domain.core.email.EmailStatus._
import domain.core.email._
import org.bson.types.ObjectId
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, MustMatchers, WordSpec}

class EmailerSpec extends WordSpec with BeforeAndAfter with MustMatchers with MockitoSugar {

  before {
    reset(emailRepository, emailSender, processLockRepository)
  }

  val emailRepository = mock[EmailRepository]
  val processLockRepository = mock[ProcessLockRepository]
  val emailSender = mock[EmailSender]
  val sender = EmailAddress("", "")
  val replyTo = EmailAddress("", "")
  val emailer = new Emailer(emailRepository, emailSender, sender, replyTo, 5, processLockRepository)
  val PROVISIONAL_ACCEPTANCE = "PROVISIONAL_ACCEPTANCE"

  "Emailer" should {

    "sendEmails zero emails if no emails in queue" in {

      when(emailRepository.findByStatus(STATUS_WAITING)).thenReturn(List())

      emailer.sendEmails()

      verify(emailSender, times(0)).sendMessage(any(), anyString(), any[List[String]], anyString(), anyString(), any(), any(), any())
      verify(emailRepository, times(0)).updateStatus(anyString(), any())
    }

    "sendEmails should send an email and set the status to sent for all emails in the queue" in {

      val emailObj1 = Email(
        caseId = Some(new ObjectId().toString),
        date = new DateTime(),
        recipient = "bob",
        subject = "subject",
        text = "text",
        html = "<html>data<html>",
        status = STATUS_WAITING,
        emailType = PROVISIONAL_ACCEPTANCE)
      val emailObj2 = Email(
        caseId = Some(new ObjectId().toString),
        date = new DateTime(),
        recipient = "bob",
        subject = "subject",
        text = "text",
        html = "<html>data<html>",
        status = STATUS_WAITING,
        emailType = PROVISIONAL_ACCEPTANCE)
      val emailList = List(emailObj1, emailObj2)
      when(emailRepository.findByStatus(STATUS_WAITING)).thenReturn(emailList)

      emailer.sendEmails()

      verify(emailSender, times(2)).sendMessage(any(), anyString(), any[List[String]], anyString(), any(), any(),any(), any())
      verify(emailRepository, times(2)).updateStatus(anyString(), any())
    }
  }
}
