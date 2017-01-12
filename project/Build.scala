

import sbt._
import sbt.Keys._
import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyPlugin.autoImport._


object KafkaBenchmarkBuild extends Build {

  lazy val root = Project(
    id = "kafka-benchmark",
    base = file("."),
    settings = kbSettings,
    aggregate = aggregatedProjects
  ).disablePlugins(AssemblyPlugin)

  lazy val kafkaSpark = Project("kafka-spark", file("kafka-spark"), settings = kafkaSparkSettings)

  lazy val kafkaStorm = Project("kafka-storm", file("kafka-storm"), settings = kafkaStormSettings)

  val scalacOptionsList = Seq("-encoding", "UTF-8", "-unchecked", "-optimize", "-deprecation",
    "-feature")

  def aggregatedProjects: Seq[ProjectReference] = {
    Seq(kafkaStorm, kafkaSpark)
  }

  def kafkaSparkSettings = kbSettings ++ Seq(
    name := "kafka-spark-benchmark",
    scalaVersion := "2.11.8",
    autoScalaLibrary := true,
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)
  ) ++ Seq(libraryDependencies ++= Dependencies.kafkaSpark)

  def kafkaStormSettings = kbSettings ++ Seq(
    name := "kafka-storm-benchmark",
    autoScalaLibrary := true,
    scalaVersion := "2.10.6") ++ Seq(libraryDependencies ++= Dependencies.kafkaStorm)

  def kbSettings =
    Defaults.coreDefaultSettings ++ Seq (
      name := "kafka-benchmark",
      version := "0.0.1",
      organization := "com.github.scrapcodes",
      autoScalaLibrary := false,
      scalacOptions := scalacOptionsList,
      updateOptions := updateOptions.value.withCachedResolution(true),
      updateOptions := updateOptions.value.withLatestSnapshots(false),
      crossPaths := false,
      publishMavenStyle := false,
      fork := true,
      javaOptions ++= Seq("-Xmx15G", "-XX:MaxPermSize=3G", "-XX:+HeapDumpOnOutOfMemoryError")
    )
}

object Dependencies {

  val kafkaClient = "org.apache.kafka" % "kafka-clients" % "0.10.0.1"

  val sparkVersion = "2.2.0-xx1"

  val stormVersion = "1.0.2"

  val spark = "org.apache.spark" %% "spark-sql" % sparkVersion % "provided"
  val sparkSqlKafka10 = "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion intransitive()
  val scalatest = "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  val storm = "org.apache.storm" % "storm-core" % stormVersion exclude("org.slf4j",
    "log4j-over-slf4j") // % "provided"
  val stormKafkaClient = "org.apache.storm" % "storm-kafka-client" % stormVersion // % "provided"

  val kafkaSpark = Seq(kafkaClient, spark, sparkSqlKafka10, scalatest)
  
  val kafkaStorm = Seq(storm, stormKafkaClient, scalatest)
  
}
