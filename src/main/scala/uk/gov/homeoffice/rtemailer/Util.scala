package uk.gov.homeoffice.rtemailer

import uk.gov.homeoffice.mongo.casbah.MongoDBObject
import org.joda.time.DateTime
import org.bson.types.ObjectId
import uk.gov.homeoffice.mongo.casbah.MongoDBList
import cats.data.EitherT

import uk.gov.homeoffice.rtemailer.model._

object Util {

  implicit class JavaOptionalOps[A](val underlying :java.util.Optional[A]) extends AnyVal {
    def asScalaOption() :Option[A] = if (underlying.isEmpty) None else Some(underlying.get())
  }

  implicit class ScalaMapOps(val underlying :Map[String, Object]) extends AnyVal {
    def asJavaMap() :java.util.HashMap[String, Object] = {
      val javaMap = new java.util.HashMap[String, Object]()
      underlying.foreach { case (k, v) => javaMap.put(k, v) }
      javaMap
    }
  }

  def extractDBField(dbObj :MongoDBObject, fieldName :String) :Option[TemplateLookup] = {

    scala.util.Try(dbObj.getAs[Any](fieldName).map {
      case d :org.joda.time.DateTime => TDate(d)
      case d :java.util.Date => TDate(new DateTime(d))
      // currently only support for lists of strings.
      case l :MongoDBList[_] => TList(l.toList.map(_.toString))
      case o :ObjectId => TString(o.toHexString)
      case true => TString("yes")
      case false => TString("no")
      case anyStringable => TString(anyStringable.toString())
    }).toOption.flatten
  }

  def appResultCollect[A, B](in :List[Either[A, B]])/* (implicit v :Semigroup[A]) */ :Either[A, List[B]] = {
    in.collect { case Left(appError) => appError } match {
      case Nil => Right(EitherT(in).collectRight)
      case listOfErrors => Left(listOfErrors.head)
      //case listOfErrors => Left(v.combine(listOfErrors))
    }
  }
}
