name := "monte-carlo-scala"

version := "0.1"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "com.typesafe.akka" %% "akka-slf4j" % "2.2.3",
  "com.typesafe.akka" %% "akka-cluster" % "2.2.3",
  "com.typesafe.akka" %% "akka-remote" % "2.2.3",
  "com.typesafe.akka" %% "akka-contrib" % "2.2.3",
  "org.apache.commons" % "commons-math" % "2.2",
  "com.orientechnologies" % "orient-commons" % "1.5.1",
  "com.orientechnologies" % "orientdb-core" % "1.5.1",
  "com.orientechnologies" % "orientdb-client" % "1.5.1",
  "com.google.code.gson" % "gson" % "2.2.4",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test"
  )

atmosSettings

atmosTestSettings

com.github.retronym.SbtOneJar.oneJarSettings

ideaExcludeFolders += ".idea"

ideaExcludeFolders += ".idea_modules"