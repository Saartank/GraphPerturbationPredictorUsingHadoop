ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.2"

lazy val root = (project in file("."))
  .settings(
    name := "GraphPerturbationPredictorUsingHadoop"
  )

val scalaTestVersion = "3.2.11"
val guavaVersion = "31.1-jre"
val netBuddyVersion = "1.14.4"
val catsVersion = "2.9.0"
val apacheCommonsVersion = "2.13.0"
val jGraphTlibVersion = "1.5.2"
val guavaAdapter2jGraphtVersion = "1.5.2"
val scalaParCollVersion = "1.0.4"
val graphVizVersion = "0.18.1"


val apacheHadoopVersion = "3.3.4"
resolvers += Resolver.jcenterRepo


libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-core" % "1.3.0-alpha10",
  "ch.qos.logback" % "logback-classic" % "1.3.0-alpha10",
  "org.slf4j" % "slf4j-api" % "2.0.0-alpha5",
  "com.typesafe" % "config" % "1.4.1",
  "org.apache.hadoop" % "hadoop-common" % apacheHadoopVersion,
  "org.apache.hadoop" % "hadoop-mapreduce-client-jobclient" % apacheHadoopVersion,
  "io.circe" %% "circe-core" % "0.14.1",
  "io.circe" %% "circe-generic" % "0.14.1",
  "io.circe" %% "circe-parser" % "0.14.1",
  "org.yaml" % "snakeyaml" % "1.29",
  "org.jfree" % "jfreechart" % "1.5.3",
  "org.jfree" % "jcommon" % "1.0.23",
  "software.amazon.awssdk" % "s3" % "2.17.52",
  "com.github.tototoshi" %% "scala-csv" % "1.3.10",
  "org.scala-lang.modules" %% "scala-parallel-collections" % scalaParCollVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "org.scalatestplus" %% "mockito-4-2" % "3.2.12.0-RC2" % Test,
  "net.bytebuddy" % "byte-buddy" % netBuddyVersion,
  "com.google.guava" % "guava" % guavaVersion,
  "guru.nidi" % "graphviz-java" % graphVizVersion,
  "org.typelevel" %% "cats-core" % catsVersion,
  "commons-io" % "commons-io" % apacheCommonsVersion,
  "org.jgrapht" % "jgrapht-core" % jGraphTlibVersion,
  "org.jgrapht" % "jgrapht-guava" % guavaAdapter2jGraphtVersion


)

excludeFilter in assembly := "META-INF/license/*"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}