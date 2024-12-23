val Http4sVersion = "0.23.24"
val CirceVersion = "0.14.6"
val MunitVersion = "0.7.29"
val LogbackVersion = "1.4.14"
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
    version := "2.0.1",
    scalaVersion := "2.13.14",
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-ember-server" % Http4sVersion,
      "org.http4s"      %% "http4s-ember-client" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"        % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "io.circe"        %% "circe-generic"       % CirceVersion,
      "io.circe"        %% "circe-parser"        % CirceVersion,
      "org.scalameta"   %% "munit"               % MunitVersion           % Test,
      "org.typelevel"   %% "munit-cats-effect-3" % MunitCatsEffectVersion % Test,
      "ch.qos.logback"  %  "logback-classic"     % LogbackVersion         % Runtime,
      "org.scalameta"   %% "svm-subs"            % "20.2.0",
      "uk.gov.homeoffice" %% "rtp-email-lib"     % "4.0.3-g4189841",
      "com.typesafe"     % "config"              % "1.4.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "com.github.eikek" %% "emil-common" % "0.15.0",
      "com.github.eikek" %% "emil-javamail" % "0.15.0",
      "uk.gov.service.notify" % "notifications-java-client" % "3.19.2-RELEASE",
      "com.outr" %% "hasher" % "1.2.2",
      "com.github.gphat" %% "censorinus" % "2.1.16",
      "org.tpolecat" %% "skunk-core" % "0.6.3",
      "org.tpolecat" %% "skunk-circe" % "0.6.3"
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.3" cross CrossVersion.full),
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

assemblyJarName in assembly := "rt-emailer.jar"
test in assembly := {}

assemblyMergeStrategy in assembly := {
  case PathList("javax", "activation", _*) => MergeStrategy.first
  case PathList("javax", "mail", _*) => MergeStrategy.first
  case PathList("com", "sun", _*) => MergeStrategy.first
  case "META-INF/io.netty.versions.properties" => MergeStrategy.first
  case "META-INF/mime.types" => MergeStrategy.first
  case "META-INF/mailcap.default" => MergeStrategy.first
  case "META-INF/mailcap" => MergeStrategy.first
  case "META-INF/mimetypes.default" => MergeStrategy.first
  case "META-INF/gfprobe-provider.xml" => MergeStrategy.first
  case "META-INF/javamail-providers.xml" => MergeStrategy.first
  case "META-INF/native-image/org.mongodb/bson/native-image.properties" => MergeStrategy.first
  case d if d.endsWith(".jar:module-info.class") => MergeStrategy.first
  case d if d.endsWith("module-info.class") => MergeStrategy.first
  case d if d.endsWith("/MatchersBinder.class") => MergeStrategy.discard
  case d if d.endsWith("/ArgumentsProcessor.class") => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

assemblyExcludedJars in assembly := {
  val cp = (fullClasspath in assembly).value
  cp filter { f => f.data.getName == "mailapi-1.4.3.jar" || f.data.getName == "gimap-2.0.1.jar" || f.data.getName == "imap-2.0.1.jar" }
}

