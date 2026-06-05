import sbt._

object Dependencies {
  val http4sVersion = "0.23.30"
  val circeVersion = "0.14.14"
  val munitVersion = "1.1.1"
  val logbackVersion = "1.5.18"
  val munitCatsEffectVersion = "1.0.7"

  val commonDependencies: Seq[ModuleID] = Seq.empty

  val compileDependencies: Seq[ModuleID] = Seq(
    "org.http4s"                 %% "http4s-ember-server"       % http4sVersion,
    "org.http4s"                 %% "http4s-ember-client"       % http4sVersion,
    "org.http4s"                 %% "http4s-circe"              % http4sVersion,
    "org.http4s"                 %% "http4s-dsl"                % http4sVersion,
    "io.circe"                   %% "circe-generic"             % circeVersion,
    "io.circe"                   %% "circe-parser"              % circeVersion,
    "ch.qos.logback"              % "logback-classic"           % logbackVersion % Runtime,
    "uk.gov.homeoffice"          %% "rtp-email-lib"             % "4.0.12-gc336ec4",
    "com.typesafe"                % "config"                    % "1.4.3",
    "com.typesafe.scala-logging" %% "scala-logging"             % "3.9.5",
    "com.github.eikek"           %% "emil-common"               % "0.19.0",
    "com.github.eikek"           %% "emil-javamail"             % "0.19.0",
    "uk.gov.service.notify"       % "notifications-java-client" % "5.2.1-RELEASE",
    "com.outr"                   %% "hasher"                    % "1.2.3",
    "org.tpolecat"               %% "skunk-core"                % "0.6.4",
    "org.tpolecat"               %% "skunk-circe"               % "0.6.4"
  )

  val testDependencies: Seq[ModuleID] = Seq(
    "org.scalameta" %% "munit"               % munitVersion,
    "org.typelevel" %% "munit-cats-effect-3" % munitCatsEffectVersion
  ).map(_ % Test)

  val all: Seq[ModuleID] = commonDependencies ++ compileDependencies ++ testDependencies
}
