import com.typesafe.sbt.packager.archetypes.JavaServerAppPackaging
import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoPlugin
import BuildInfoGenerator.buildInfoGeneratorSettings

object Build extends Build {

  val dependencies = Seq(
    "org.clapper" %% "grizzled-slf4j" % "1.0.2",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "joda-time" % "joda-time" % "2.5",
    "org.joda" % "joda-convert" % "1.7",
    "org.mongodb" %% "casbah-core" % "3.1.1",
    "com.github.scopt" %% "scopt" % "3.2.0" withSources(),
    "uk.gov.homeoffice" %% "rtp-email-lib" % "3.2.1-SNAPSHOT" withSources()
  )

  lazy val emailer = Project("RT-Emailer", file("."))
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
    .settings(buildInfoGeneratorSettings: _*)

  def existsLocallyAndNotOnJenkins(filePath: String) = file(filePath).exists && !file(filePath + "/nextBuildNumber").exists()

}
