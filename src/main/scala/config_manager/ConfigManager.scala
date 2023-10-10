package config_manager

import com.typesafe.config.{Config, ConfigFactory}

object ConfigManager {

  private var config: Config = ConfigFactory.load()

  def getConfig: Config = config

  def overrideWithArgs(args: Array[String]): Unit = {
    if (args.length == 7) {
      val overrides = ConfigFactory.parseString(
        s"""
           |locations.originalGraph="${args(0)}"
           |locations.perturbedGraph="${args(1)}"
           |locations.shardDir="${args(2)}"
           |locations.mapRedOutDir="${args(3)}"
           |locations.groundTruthYAML="${args(4)}"
           |locations.analysisOutputDir="${args(5)}"
           |locations.env="${args(6)}"
           |""".stripMargin)
      config = overrides.withFallback(config)
    }
  }

}
