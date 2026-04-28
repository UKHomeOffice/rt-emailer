import sbt._
import sbt.Keys._
import sbtassembly.MergeStrategy
import sbtassembly.AssemblyPlugin.autoImport._

object AssemblySettings {
  val packaging: Seq[Def.Setting[_]] = Seq(
    assembly / assemblyJarName := "rt-emailer.jar",
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      case PathList("javax", "activation", _*)     => MergeStrategy.first
      case PathList("javax", "mail", _*)           => MergeStrategy.first
      case PathList("com", "sun", _*)              => MergeStrategy.first
      case "logback.xml"                           => MergeStrategy.first
      case "META-INF/io.netty.versions.properties" => MergeStrategy.first
      case "META-INF/mime.types"                   => MergeStrategy.first
      case "META-INF/mailcap.default"              => MergeStrategy.first
      case "META-INF/mailcap"                      => MergeStrategy.first
      case "META-INF/mimetypes.default"            => MergeStrategy.first
      case "META-INF/gfprobe-provider.xml"         => MergeStrategy.first
      case "META-INF/javamail-providers.xml"       => MergeStrategy.first
      case "META-INF/native-image/org.mongodb/bson/native-image.properties" =>
        MergeStrategy.first
      case "META-INF/native-image/reflect-config.json"     => MergeStrategy.first
      case "META-INF/native-image/native-image.properties" => MergeStrategy.first
      case "draftv4/schema"                                => MergeStrategy.first
      case d if d.endsWith(".jar:module-info.class")       => MergeStrategy.first
      case d if d.endsWith("module-info.class")            => MergeStrategy.first
      // Drop duplicate classes from transitive libs that clash at assembly time.
      case d if d.endsWith("/MatchersBinder.class")        => MergeStrategy.discard
      // Drop duplicate arg parser classes from overlapping dependencies.
      case d if d.endsWith("/ArgumentsProcessor.class")    => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    assembly / assemblyExcludedJars := {
      val cp = (assembly / fullClasspath).value
      cp filter { f =>
        f.data.getName == "mailapi-1.4.3.jar" || f.data
          .getName == "gimap-2.0.1.jar" || f.data.getName == "imap-2.0.1.jar"
      }
    }
  )
}
