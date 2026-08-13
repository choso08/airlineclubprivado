name := """airline-data"""

version := "2.1"

scalaVersion := "2.13.14"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0",
  //"org.xerial" % "sqlite-jdbc" % "3.8.11.2",
  // Back on 5.1.49 after 8.4.0 took the live game down. The scrollable
  // statements it needed are staying - both drivers honour those - so the
  // upgrade is one line here and one in Constants when it is understood.
  "mysql" % "mysql-connector-java" % "5.1.49",
  "org.apache.pekko" %% "pekko-actor" % "1.0.3",
  "org.apache.pekko" %% "pekko-stream" % "1.0.3",
  "org.apache.pekko" %% "pekko-remote" % "1.0.3",
  "org.apache.pekko" %% "pekko-testkit" % "1.0.3",
  "org.apache.pekko" %% "pekko-cluster" % "1.0.3",
  // The web half runs org.playframework play-json 3.0.4 and this one asked for
  // com.typesafe.play 2.7.4. Both put their classes in play.api.libs.json, so
  // the two halves were compiled against different libraries occupying the
  // same package and only one of them was on the classpath at run time. Same
  // one on both sides now.
  "org.playframework" %% "play-json" % "3.0.4",
  "com.mchange" % "c3p0" % "0.9.5.5",
  // 22.0 is from 2017, and the web half already runs 32.1.3 - so the two
  // halves were compiled against different Guavas and only one of them was on
  // the classpath at run time. Same version on both sides now.
  "com.google.guava" % "guava" % "32.1.3-jre")

  
  
  
