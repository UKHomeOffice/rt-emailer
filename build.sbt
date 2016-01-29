fork in run := true

publishTo := {
  val artifactory = "http://artifactory.registered-traveller.homeoffice.gov.uk/"

  if (isSnapshot.value)
    Some("snapshot" at artifactory + "artifactory/libs-snapshot-local")
  else
    Some("release"  at artifactory + "artifactory/libs-release-local")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

// Enable publishing the jar produced by `test:package`
publishArtifact in (Test, packageBin) := true

// Enable publishing the test API jar
publishArtifact in (Test, packageDoc) := true

// Enable publishing the test sources jar
publishArtifact in (Test, packageSrc) := true

logLevel in assembly := Level.Info

assemblyExcludedJars in assembly := {
  val testDependencies = (fullClasspath in Test).value
    .sortWith((f1, f2) => f1.data.getName < f2.data.getName)

  println("=========================== Test Dependencies ========================= \n" + testDependencies.map(_.data.getAbsolutePath).mkString("\n"))

  val compileDependencies = (fullClasspath in Compile).value
    .filterNot(_.data.getName.endsWith("-tests.jar"))
    .filterNot(_.data.getName.startsWith("mockito-"))
    .filterNot(_.data.getName.startsWith("specs2-"))
    .filterNot(_.data.getName.startsWith("scalatest"))
    .sortWith((f1, f2) => f1.data.getName < f2.data.getName)

  println(s"=========================== Compile Dependencies =========================== \n" + compileDependencies.map(_.data.getName).mkString("\n"))

  val testOnlyDependencies = testDependencies.diff(compileDependencies).sortWith((f1, f2) => f1.data.getName < f2.data.getName)
  println(s"=========================== Test ONLY Dependencies ===========================\n" + testOnlyDependencies.map(_.data.getName).mkString("\n"))
  testOnlyDependencies
}

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith ".java" => MergeStrategy.discard
  case _ => MergeStrategy.first
}

test in assembly := {}