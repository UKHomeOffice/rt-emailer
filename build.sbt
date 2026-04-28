import Dependencies._
import Repositories.repoResolvers
import AssemblySettings.packaging

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

resolvers ++= repoResolvers

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning, BuildInfoPlugin)
  .settings(
    organization := "uk.gov.homeoffice",
    name         := "rt-emailer",
    scalaVersion := "3.7.1",
    libraryDependencies ++= all,
    testFrameworks += new TestFramework("munit.Framework")
  )
  .settings(packaging)

git.useGitDescribe      := true
git.gitDescribePatterns := Seq("v*.*")
git.gitTagToVersionNumber := { tag: String =>
  val branchTag = if (git.gitCurrentBranch.value == "master") ""
  else "-" + git.gitCurrentBranch.value
  val uncommit = if (git.gitUncommittedChanges.value) "-U" else ""

  tag match {
    case v if v.matches("v\\d+.\\d+") =>
      Some(s"$v.0$branchTag$uncommit".drop(1))
    case v if v.matches("v\\d+.\\d+-.*") =>
      Some(s"${v.replaceFirst("-", ".")}$branchTag$uncommit".drop(1))
    case _ => None
  }
}

buildInfoOptions += BuildInfoOption.BuildTime

//TODO: need to investigate, if we can remove the -rewrite option and the source 3.7-migration rewrite rule now that the code has been migrated to Scala 3.7.1
// The -rewrite option is used to apply the source 3.7-migration rewrite rule, which is needed to rewrite some of the code that was using deprecated features in Scala 3.7.0 to be compatible with Scala 3.7.1. Once we have removed all the deprecated features, we can remove the -rewrite option and the source 3.7-migration rewrite rule.
// other option -Xfatal-warnings is used to fail the build if there are any warnings, which is useful to ensure that we don't have any deprecated features in our code. We can keep this option to ensure that we don't introduce any new deprecated features in the future.

scalacOptions ++= Seq("-new-syntax", "-rewrite", "source 3.7-migration")
