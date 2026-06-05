import sbt._
import com.github.sbt.git.GitPlugin.autoImport.git

object AppGitVersioningSettings {
  val all: Seq[Setting[_]] = Seq(
    git.useGitDescribe := true,
    git.gitDescribePatterns := Seq("v*.*"),
    git.gitTagToVersionNumber := { tag: String =>
      val branchTag = if (git.gitCurrentBranch.value == "master") "" else "-" + git.gitCurrentBranch.value
      val uncommit = if (git.gitUncommittedChanges.value) "-U" else ""

      tag match {
        case v if v.matches("v\\d+.\\d+")    => Some(s"$v.0${branchTag}${uncommit}".drop(1))
        case v if v.matches("v\\d+.\\d+-.*") => Some(s"${v.replaceFirst("-", ".")}${branchTag}${uncommit}".drop(1))
        case _                                 => None
      }
    }
  )
}
