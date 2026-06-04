credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
resolvers ++= AppResolvers.all

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning, BuildInfoPlugin)
  .settings(
    organization := "uk.gov.homeoffice",
    name := "rt-emailer",
    scalaVersion := "3.7.1",
    libraryDependencies ++= AppDependencies.all,
    testFrameworks += new TestFramework("munit.Framework")
  )
  .settings(AppGitVersioningSettings.all)
  .settings(AppAssemblySettings.all)

buildInfoOptions += BuildInfoOption.BuildTime

scalacOptions ++= Seq("-new-syntax", "-rewrite", "source 3.7-migration")
