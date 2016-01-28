import sbt._
import sbtassembly.AssemblyPlugin.autoImport._
import com.typesafe.sbt.packager.Keys._
import sbt.Keys._
import com.typesafe.sbt.packager.archetypes.{JavaServerAppPackaging, ServerLoader}
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.config._

object Build extends Build {
  val version_conf = ConfigFactory.parseFile(new File("version.properties")).resolve()
  val conf = ConfigFactory.parseFile(new File("rpm.conf")).resolve()
  val appName = conf.getString("app.name")
  val appVersion = "1.0-SNAPSHOT"
  val rpmVersion = version_conf.getString("version")
  val appSummary = conf.getString("app.summary")
  val appDescription = conf.getString("app.description")

  // Rpm specific settings
  val rpmrelease = if (System.getenv("BUILD_NUMBER") == null) "1" else System.getenv("BUILD_NUMBER")
  val rpmurl = Option(conf.getString("rpm.url"))
  val rpmgroup = Option(conf.getString("rpm.group"))
  val rpmlicense = Option(conf.getString("rpm.license"))
  val rpmvendor = conf.getString("rpm.vendor")
  val rpmpackager = Option(conf.getString("rpm.packager"))
  val rpmpre = Option( """|getent group %{name} >/dev/null || groupadd -r %{name}
                         |getent passwd %{name} >/dev/null || /usr/sbin/useradd -M -r -g %{name} -c "%{summary}" -d "/usr/share/%{name}" %{name}
                         | """.stripMargin)
  val rpmpost = Option("/sbin/chkconfig --add %{name}")
  val rpmpreun = Option("if [ \"$1\" = 0 ] ; then service %{name} stop > /dev/null 2>&1 ; chkconfig --del %{name} ; fi ; exit 0")
  val rpmpostun = Option("if [ \"$1\" -ge 1 ]; then service %{name} condrestart >/dev/null 2>&1 ; fi ; exit 0")
  val rpmreqs: Seq[String] = conf.getString("rpm.reqs").split(",")

  val dependencies = Seq(
    "org.clapper" %% "grizzled-slf4j" % "1.0.2",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "joda-time" % "joda-time" % "2.5",
    "org.joda" % "joda-convert" % "1.7",
    "org.mongodb" %% "casbah-core" % "2.7.4",
    "com.github.scopt" %% "scopt" % "3.2.0",
    "org.yaml" % "snakeyaml" % "1.4",
    "org.apache.commons" % "commons-email" % "1.3.2",
    "commons-io" % "commons-io" % "2.4",
    "com.icegreen" % "greenmail" % "1.3.1b" % "test",
    "com.github.finagle" %% "finch-core" % "0.3.0",
    "com.github.finagle" %% "finch-json" % "0.3.0",
    "org.specs2" %% "specs2-core" % "3.6.2" % "test" withSources(),
    "org.specs2" %% "specs2-mock" % "3.6.2" % "test" withSources(),
    "org.specs2" %% "specs2-matcher-extra" % "3.6.2" % "test" withSources(),
    "org.specs2" %% "specs2-junit" % "3.6.2" % "test" withSources(),
    "org.mockito" % "mockito-all" % "1.10.19" % "test" withSources(),
    "org.scalatest" %% "scalatest" % "2.2.4" % "test" withSources()
  )

  lazy val emailer = Project(appName, file("."))
    .enablePlugins(JavaServerAppPackaging)
    .settings(
      version := appVersion,
      organization := "uk.gov.homeoffice",
      scalaVersion := "2.11.7")
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
    .settings(mappings in Universal ++= (baseDirectory.value / "ops/config.yml" ***).filter(_.isFile).get map {
      file: File => file -> ("conf/" + "rt-emailer.yml")
    })
    .settings(
      name in Rpm := appName,
      version in Rpm := rpmVersion,
      rpmRelease := rpmrelease,
      packageSummary := appSummary,
      rpmVendor := rpmvendor,
      rpmUrl := rpmurl,
      rpmLicense := rpmlicense,
      packageDescription := appDescription,
      rpmPre := rpmpre,
      rpmPost := rpmpost,
      rpmPreun := rpmpreun,
      rpmPostun := rpmpostun,
      rpmGroup := rpmgroup,
      rpmPackager := rpmpackager,
      rpmRequirements := rpmreqs
    )
    .settings(assemblyJarName in assembly := s"$appName-$appVersion.jar")

  def existsLocallyAndNotOnJenkins(filePath: String) = file(filePath).exists && !file(filePath + "/nextBuildNumber").exists()

  val domainPath = "../../rtp-caseworker-domain-lib"

  lazy val root = if (existsLocallyAndNotOnJenkins(domainPath)) {
    println("================")
    println("Build Locally em")
    println("================")

    val domain = ProjectRef(file(domainPath), "rtp-caseworker-domain-lib")

    emailer.dependsOn(domain % "test->test;compile->compile")
  } else {
    println("===================")
    println("Build on Jenkins em")
    println("===================")

    emailer.settings(
      libraryDependencies ++= Seq(
        "uk.gov.homeoffice" %% "rtp-caseworker-domain-lib" % "1.2.0-SNAPSHOT" withSources(),
        "uk.gov.homeoffice" %% "rtp-caseworker-domain-lib" % "1.2.0-SNAPSHOT" % Test classifier "tests" withSources()
      )
    )
  }
}