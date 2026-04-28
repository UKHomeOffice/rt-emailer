import sbt._

object Dependencies {
  val Http4sVersion          = "0.23.30"
  val CirceVersion           = "0.14.14"
  val MunitVersion           = "1.1.1"
  val LogbackVersion         = "1.5.18"
  val MunitCatsEffectVersion = "1.0.7"

  val all: Seq[ModuleID] = Seq(
    "org.http4s"                 %% "http4s-ember-server"       % Http4sVersion,
    "org.http4s"                 %% "http4s-ember-client"       % Http4sVersion,
    "org.http4s"                 %% "http4s-circe"              % Http4sVersion,
    "org.http4s"                 %% "http4s-dsl"                % Http4sVersion,
    "io.circe"                   %% "circe-generic"             % CirceVersion,
    "io.circe"                   %% "circe-parser"              % CirceVersion,
    "org.scalameta"              %% "munit"                     % MunitVersion           % Test,
    "org.typelevel"              %% "munit-cats-effect-3"       % MunitCatsEffectVersion % Test,
    "ch.qos.logback"              % "logback-classic"           % LogbackVersion         % Runtime,
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
}
