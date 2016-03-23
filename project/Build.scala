import com.typesafe.config._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.archetypes.JavaServerAppPackaging
import sbt.Keys._
import sbt._

object Build extends Build {
  val version_conf = ConfigFactory.parseFile(new File("version.properties")).resolve()
  val conf = ConfigFactory.parseFile(new File("rpm.conf")).resolve()
  val appName = conf.getString("app.name")
  val appVersion = "1.4.2"
  val appSummary = conf.getString("app.summary")
  val appDescription = conf.getString("app.description")

  val dependencies = Seq(
    "org.clapper" %% "grizzled-slf4j" % "1.0.2",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "joda-time" % "joda-time" % "2.5",
    "org.joda" % "joda-convert" % "1.7",
    "org.mongodb" %% "casbah-core" % "2.7.4",
    "org.yaml" % "snakeyaml" % "1.4"
  )

  lazy val emailer = Project(appName, file("."))
    .enablePlugins(JavaServerAppPackaging)
    .settings(
      version := appVersion,
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
    })

  def existsLocallyAndNotOnJenkins(filePath: String) = file(filePath).exists && !file(filePath + "/nextBuildNumber").exists()

  val libPath = "../rtp-email-lib"

  lazy val root = if (existsLocallyAndNotOnJenkins(libPath)) {
    println("================")
    println("Build Locally em")
    println("================")

    val lib = ProjectRef(file(libPath), "rtp-email-lib")

    emailer.dependsOn(lib % "test->test;compile->compile")
  } else {
    println("===================")
    println("Build on Jenkins em")
    println("===================")

    emailer.settings(
      libraryDependencies ++= Seq(
        "uk.gov.homeoffice" %% "rtp-email-lib" % "1.0.2" withSources(),
        "uk.gov.homeoffice" %% "rtp-email-lib" % "1.0.2" % Test classifier "tests" withSources()
      )
    )
  }
}