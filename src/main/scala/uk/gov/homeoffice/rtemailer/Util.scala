package uk.gov.homeoffice.rtemailer

import com.mongodb.casbah.commons.MongoDBObject
import org.joda.time.DateTime
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBList

import uk.gov.homeoffice.rtemailer.model._

object Util {

  implicit class JavaOptionalOps[A](val underlying :java.util.Optional[A]) extends AnyVal {
    def asScalaOption() :Option[A] = if (underlying.isEmpty) None else Some(underlying.get())
  }

  implicit class ScalaMapOps(val underlying :Map[String, String]) extends AnyVal {
    def asJavaMap() :java.util.HashMap[String, Object] = {
      val javaMap = new java.util.HashMap[String, Object]()
      underlying.foreach { case (k, v) => javaMap.put(k, v) }
      javaMap
    }
  }

  def extractDBField(dbObj :MongoDBObject, fieldName :String) :Option[TemplateLookup] = {
    import com.mongodb.casbah.Imports._

    scala.util.Try(dbObj.getAs[Any](fieldName).map {
      case d :org.joda.time.DateTime => TDate(d)
      case d :java.util.Date => TDate(new DateTime(d))
      // currently only support for lists of strings.
      case l :MongoDBList => TList(l.map(_.toString).toList)
      case o :ObjectId => TString(o.toHexString)
      case true => TString("yes")
      case false => TString("no")
      case anyStringable => TString(anyStringable.toString())
    }).toOption.flatten
  }

}
