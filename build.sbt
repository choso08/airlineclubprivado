// The whole game, as one build.
//
// The two halves used to be two separate sbt builds: airline-data published
// itself to the local Ivy cache as "default" %% "airline-data" % "2.1", and
// airline-web asked for that by name and version. The version has not moved in
// years while the contents change on every build, so the resolver was entitled
// to trust its cached answer about what airline-data needs - and did. A
// dependency added on this side could be missing from the web site's classpath
// with everything still compiling, which is exactly how a database driver was
// published, never delivered, and took an evening to find.
//
// dependsOn removes the whole class of problem: the web site now compiles
// against the code next door, not against a copy of it.
lazy val airlineData = project.in(file("airline-data"))

lazy val airlineWeb = project.in(file("airline-web"))
  .enablePlugins(PlayScala)
  .dependsOn(airlineData)

lazy val root = project.in(file("."))
  .aggregate(airlineData, airlineWeb)
  .settings(
    name := "airline",
    // Nothing lives at the root; it is here to tie the two halves together.
    publish / skip := true
  )
