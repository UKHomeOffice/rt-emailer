import com.typesafe.config._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.archetypes.JavaServerAppPackaging
import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoPlugin
import BuildInfoGenerator.buildInfoGeneratorSettings

object Build extends Build {
  val conf = ConfigFactory.parseFile(new File("rpm.conf")).resolve()
  val appName = conf.getString("app.name")
  val appSummary = conf.getString("app.summary")
  val appDescription = conf.getString("app.description")

  val dependencies = Seq(
    "org.clapper" %% "grizzled-slf4j" % "1.0.2",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "joda-time" % "joda-time" % "2.5",
    "org.joda" % "joda-convert" % "1.7",
    "org.mongodb" %% "casbah-core" % "3.1.1",
    "org.yaml" % "snakeyaml" % "1.4",
    "com.github.scopt" %% "scopt" % "3.2.0" withSources(),
    "uk.gov.homeoffice" %% "rtp-email-lib" % "3.2.1-SNAPSHOT" withSources()
  )

  lazy val emailer = Project(appName, file("."))
    .enablePlugins(JavaServerAppPackaging,BuildInfoPlugin)
    .settings(
      organization := "uk.gov.homeoffice",
      scalaVersion := "2.11.8")
    .settings(resolvers ++= Seq(
      "Artifactory Snapshot Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-snapshot-local/",
      "Artifactory Release Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-release-local/",
      "TypeSafe" at "http://repo.typesafe.com/typesafe/releases",
      "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases")
    )
    .settings(libraryDependencies ++= dependencies)
    .settings(mappings in Universal ++= (baseDirectory.value / "bin/" ***).filter(_.isFile).get map {
      file: File => file -> ("bin/" + file.getName)
    }).settings(buildInfoGeneratorSettings: _*)

  def existsLocallyAndNotOnJenkins(filePath: String) = file(filePath).exists && !file(filePath + "/nextBuildNumber").exists()

//  val libPath = "../rtp-email-lib"
//
//  lazy val root = if (existsLocallyAndNotOnJenkins(libPath)) {
//    println("================")
//    println("Build Locally em")
//    println("================")
//
//    val lib = ProjectRef(file(libPath), "rtp-email-lib")
//
//    emailer.dependsOn(lib % "test->test;compile->compile")
//  } else {
//    println("===================")
//    println("Build on Jenkins em")
//    println("===================")
//
//    emailer.settings(
//      libraryDependencies ++= Seq(
//        "uk.gov.homeoffice" %% "rtp-email-lib" % "1.0.3" withSources(),
//        "uk.gov.homeoffice" %% "rtp-email-lib" % "1.0.3" % Test classifier "tests" withSources()
//      )
//    )
//  }
}
