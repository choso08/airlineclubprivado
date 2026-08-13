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
  // Lets Pekko report through the same logger as everything else, so a failure
  // inside an actor arrives with its stack trace instead of one line.
  // Switched on by run-simulation.sh, per process - not in application.conf,
  // which the web half also ends up reading.
  "org.apache.pekko" %% "pekko-slf4j" % "1.0.3",
  // The web half runs org.playframework play-json 3.0.4 and this one asked for
  // com.typesafe.play 2.7.4. Both put their classes in play.api.libs.json, so
  // the two halves were compiled against different libraries occupying the
  // same package and only one of them was on the classpath at run time. Same
  // one on both sides now.
  "org.playframework" %% "play-json" % "3.0.4",
  "com.mchange" % "c3p0" % "0.9.5.5",
  // The simulation had no logging backend, so SLF4J fell back to its "throw
  // everything away" mode and printed one warning about it per start. Every
  // exception the simulation hit went into that hole - which is why the night
  // the MySQL 8 driver took the game down left no trace in journalctl.
  //
  // Both are pinned to exactly what the web half already resolves through
  // Play 3.0.5, so the two halves share one logging library instead of two.
  // c3p0 was quietly dragging slf4j-api 1.7.36 in behind everyone's back.
  //
  // These are NOT "provided": sbt leaves provided dependencies off the run
  // classpath, which is the whole point of them, so the simulation would go on
  // finding no provider. The config file is what has to stay out of the
  // published jar, and it does - see airline-data/logback-simulation.xml.
  "org.slf4j" % "slf4j-api" % "2.0.13",
  "ch.qos.logback" % "logback-classic" % "1.5.6",
  // 22.0 is from 2017, and the web half already runs 32.1.3 - so the two
  // halves were compiled against different Guavas and only one of them was on
  // the classpath at run time. Same version on both sides now.
  "com.google.guava" % "guava" % "32.1.3-jre")

  
  
  
