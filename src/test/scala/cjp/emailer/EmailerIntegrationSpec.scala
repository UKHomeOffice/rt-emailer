package cjp.emailer

import caseworkerdomain.lock.ProcessLockRepository
import org.scalatest.{BeforeAndAfter, WordSpec}
import org.scalatest.matchers.MustMatchers
import domain.core.email._
import domain.core.email.EmailStatus._
import org.scalatest.mock.MockitoSugar
import org.bson.types.ObjectId
import org.joda.time.DateTime
import scala.Some
import java.nio.file.Files
import java.io.{InputStream, FileWriter}
import javax.mail.util.SharedByteArrayInputStream
import java.util.Scanner

class EmailerIntegrationSpec extends WordSpec with MongoSpecSupport with GreenMailHelper with MustMatchers with MockitoSugar with BeforeAndAfter {
  val sender = EmailAddress("jonny.cavell@gmail.com", "Jonny Cavell")
  val replyTo = EmailAddress("replyto@test.com", "Reply To")
  val emailRepository = new EmailRepository(mongoConnectorForTest)
  val processLockRepository = new ProcessLockRepository(mongoConnectorForTest)

  val PROVISIONAL_ACCEPTANCE = "PROVISIONAL_ACCEPTANCE"

  "Sending an email message via the Emailer " should {

    "result in that message with invalid email not ending up in the GreenMail message queue" in {
      val emailSender = new EmailSender(GreenMailHelper.smtpConfig)
      val emailer = new Emailer(emailRepository, emailSender, sender, replyTo, 5, processLockRepository)

      val emailObj1 = Email(
        caseId = Some(new ObjectId().toString),
        date = new DateTime(),
        recipient = "bob@bob.com",
        subject = "subject",
        text = "text",
        html = "data",
        status = STATUS_WAITING,
        emailType = PROVISIONAL_ACCEPTANCE)

      emailRepository.insert(emailObj1)

      emailer.sendEmails()

      // Longer time needed for virtual environments with less resources
      Thread.sleep(1000)

      GreenMailHelper.getReceivedMessages.size mustBe 1
    }

    "result in that message with email ending up in the GreenMail message queue" in {

      val emailSender = new EmailSender(GreenMailHelper.smtpConfig)
      val emailer = new Emailer(emailRepository, emailSender, sender, replyTo, 5, processLockRepository)

      val emailObj1 = Email(
        caseId = Some(new ObjectId().toString),
        date = new DateTime(),
        recipient = "bob@bob.com",
        subject = "subject",
        text = "text",
        html = "data",
        status = STATUS_WAITING,
        emailType = PROVISIONAL_ACCEPTANCE)

      val emailObj2 = Email(
        caseId = Some(new ObjectId().toString),
        date = new DateTime(),
        recipient = "bob@",
        subject = "subject",
        text = "text",
        html = "data",
        status = STATUS_WAITING,
        emailType = PROVISIONAL_ACCEPTANCE)

      emailRepository.insert(emailObj1)
      emailRepository.insert(emailObj2)

      emailer.sendEmails()

      // Longer time needed for virtual environments with less resources
      Thread.sleep(1000)

      GreenMailHelper.getReceivedMessages.size mustBe 1
    }
  }

  "Sending an email message via the EmailService " should {

    "result in that message ending up in the GreenMail message queue" in {

      val emailSender = new EmailSender(GreenMailHelper.smtpConfig)
      emailSender.sendMessage(
        sender = sender,
        recipient = "jonny.cavell@gmail.com",
        ccList = List("a@a.com", "b@b.com"),
        subject = "Your Registered Traveller application has been received",
        message = "This is some text",
        replyTo = Some(replyTo))

      // Longer time needed for virtual environments with less resources
      Thread.sleep(1000)

      GreenMailHelper.getLastMessageContent mustBe "This is some text"
      GreenMailHelper.getLastMessageCCList mustBe List("a@a.com", "b@b.com")
    }
  }
  "Sending an email with an attachment via the EmailService " should {

    "result in that message with the attachment ending up in the GreenMail message queue" in {

      val attachmentPath = Files.createTempFile("test_attachment.txt", null)
      val writer = new FileWriter(attachmentPath.toString)
      writer.write("This is an attachment")
      writer.flush()
      writer.close()

      val attachment = Attachment(attachmentPath.toString, "This is an attachment for testing", "Test attachment")

      val emailSender = new EmailSender(GreenMailHelper.smtpConfig)
      emailSender.sendMessage(
        sender = sender,
        recipient = "jonny.cavell@gmail.com",
        subject = "This is a test email with an attachment",
        message = "There should be an attachment",
        attachments = Vector(attachment),
        replyTo = Some(replyTo)
      )

      // Longer time needed for virtual environments with less resources
      Thread.sleep(1000)

      GreenMailHelper.getLastMessageContent mustBe "There should be an attachment"

      val attachmentName = GreenMailHelper.getLastReceivedMessageContent.getBodyPart(1).getFileName
      attachmentName mustBe "Test attachment"

      val attachmentContent = GreenMailHelper.getLastReceivedMessageContent.getBodyPart(1).
        getContent.asInstanceOf[SharedByteArrayInputStream]
      convertStreamToString(attachmentContent) must be("This is an attachment")
      val fromAddress = GreenMailHelper.getReceivedMessages.last.getFrom.head.toString
      fromAddress must include(sender.name)
      fromAddress must include(sender.email)
      val replyToAddress = GreenMailHelper.getReceivedMessages.last.getReplyTo.head.toString
      replyToAddress must include(replyTo.name)
      replyToAddress must include(replyTo.email)
    }
  }


  def convertStreamToString(inputStream: InputStream) = {
    val scanner = new Scanner(inputStream).useDelimiter("\\A")
    if (scanner.hasNext) scanner.next else ""
  }
}

import domain.core.mongo.MongoConnector

/* TODO Mongo interaction in specs as well as code, needs a review */
trait MongoSpecSupport {
  implicit val databaseName = "repositorytest"

  val mongoUri: String = s"mongodb://127.0.0.1:27017/$databaseName?maxPoolSize=20&waitqueuemultiple=10"

  implicit val mongoConnectorForTest = new MongoConnector(mongoUri)

  implicit val hostForTest = "localhost"
}
