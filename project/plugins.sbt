addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.5")

addSbtPlugin("com.github.sbt" % "sbt-less" % "2.0.1")

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

// Disabled: no .coffee sources exist in this project, and the plugin is only
// published to repo.scala-sbt.org, so the build breaks wherever that repo is
// unreachable. See airline-web/project/plugins.sbt for the same change.
//addSbtPlugin("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.2")