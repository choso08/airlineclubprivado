name := """airline-web"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.14"
enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  jdbc,
  ws,
  guice,
  "com.google.inject" % "guice" % "5.1.0",
  "com.google.inject.extensions" % "guice-assistedinject" % "5.1.0",
  specs2 % Test,
  "org.apache.pekko" %% "pekko-remote" % "1.0.3",
  // The other half of this repo, built next door and published to the local
  // Ivy cache by scripts/build.sh. Its version has been 2.1 for years while
  // the contents change every build, so without changing() sbt is entitled to
  // trust its cached answer - and it does. That is how a new dependency added
  // in airline-data can be missing from the web site's classpath while
  // everything still compiles: the code came across, the dependency list did
  // not.
  ("default" %% "airline-data" % "2.1").changing(),
  "com.google.api-client" % "google-api-client" % "1.30.4",
  "com.google.oauth-client" % "google-oauth-client-jetty" % "1.34.1",
  "com.google.apis" % "google-api-services-gmail" % "v1-rev103-1.25.0",
  "com.google.photos.library" % "google-photos-library-client" % "1.7.2",
  "javax.mail" % "javax.mail-api" % "1.6.2",
  "com.sun.mail" % "javax.mail" % "1.6.2"
)





resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator
