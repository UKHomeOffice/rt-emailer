import com.typesafe.sbt.SbtGit.git
import sbt.Keys._
import sbtbuildinfo.BuildInfoKeys._
import sbtbuildinfo.{BuildInfoKey, BuildInfoOption}

import scala.util.Try

object BuildInfoGenerator {

  val gitVersion = BuildInfoKey.map(git.gitHeadCommit) { case (_, v) => "gitHeadCommit" -> v.get }
  val gitBranch = BuildInfoKey.map(git.gitCurrentBranch) { case (_, v) => "gitBranch" -> v }
  val gitTag = BuildInfoKey.map(git.gitCurrentTags) { case (_, v) => "gitTag" -> Try(v.head).getOrElse("") }

  lazy val buildInfoGeneratorSettings =
    Seq(
      buildInfoKeys := Seq[BuildInfoKey](name, gitBranch, gitVersion, gitTag),
      buildInfoOptions ++= Seq(BuildInfoOption.ToJson)
    )
}