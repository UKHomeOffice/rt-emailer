import sbt._

object Repositories {
  val repoResolvers: Seq[Resolver] = Seq(
    "ACPArtifactory Lib Snapshot" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/libs-snapshot-local/",
    "ACPArtifactory Lib Release" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/libs-release-local/",
    "ACPArtifactory Ext Release" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/ext-release-local/",
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  )
}
