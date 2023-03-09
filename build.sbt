val Http4sVersion = "0.23.18"
val CirceVersion = "0.14.3"
val MunitVersion = "0.7.29"
val LogbackVersion = "1.2.11"
val MunitCatsEffectVersion = "1.0.7"

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

resolvers ++= Seq(
  "ACPArtifactory Lib Snapshot" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/libs-snapshot-local/",
  "ACPArtifactory Lib Release" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/libs-release-local/",
  "ACPArtifactory Ext Release" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/ext-release-local/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning, BuildInfoPlugin)
  .settings(
    organization := "uk.gov.homeoffice",
    name := "rt-emailer",
    version := "2.0.0",
    scalaVersion := "2.12.16",
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-ember-server" % Http4sVersion,
      "org.http4s"      %% "http4s-ember-client" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"        % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "io.circe"        %% "circe-generic"       % CirceVersion,
      "org.scalameta"   %% "munit"               % MunitVersion           % Test,
      "org.typelevel"   %% "munit-cats-effect-3" % MunitCatsEffectVersion % Test,
      "org.fusesource.jansi" % "jansi" % "1.18",
      "ch.qos.logback"  %  "logback-classic"     % LogbackVersion         % Runtime,
      "org.scalameta"   %% "svm-subs"            % "20.2.0",
      "uk.gov.homeoffice" %% "rtp-email-lib"     % "3.4.23-g446ba80-U-SNAPSHOT",
      "com.typesafe"     % "config"              % "1.4.0",
      "ch.qos.logback"   %  "logback-classic"    % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.2" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
    testFrameworks += new TestFramework("munit.Framework")
  )

git.useGitDescribe := true
git.gitDescribePatterns := Seq("v*.*")
git.gitTagToVersionNumber := { tag :String =>

  val branchTag = if (git.gitCurrentBranch.value == "master") "" else "-" + git.gitCurrentBranch.value
  val uncommit = if (git.gitUncommittedChanges.value) "-U" else ""

  tag match {
    case v if v.matches("v\\d+.\\d+") => Some(s"$v.0${branchTag}${uncommit}".drop(1))
    case v if v.matches("v\\d+.\\d+-.*") => Some(s"${v.replaceFirst("-",".")}${branchTag}${uncommit}".drop(1))
    case _ => None
  }}

buildInfoOptions += BuildInfoOption.BuildTime
