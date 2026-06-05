credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
resolvers ++= AppResolvers.all

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning, BuildInfoPlugin)
  .settings(
    organization := "uk.gov.homeoffice",
    name := "rt-emailer",
    scalaVersion := "3.7.1",
    libraryDependencies ++= Dependencies.all,
    testFrameworks += new TestFramework("munit.Framework")
  )
  .settings(AppGitVersioningSettings.all)
  .settings(AppAssemblySettings.all)

buildInfoOptions += BuildInfoOption.BuildTime

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalafmtOnCompile := true
ThisBuild / scalafixOnCompile := true

scalacOptions ++= Seq(
  "-explain",
  "-Wunused:all",
  "-Wunused:imports"
)
