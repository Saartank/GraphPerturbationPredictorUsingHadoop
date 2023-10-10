
import org.slf4j.LoggerFactory
import map_reduce.MapReduce
import post_map_reduce.ProcessMapRedOut
import config_manager.ConfigManager

import load_utils.GraphLoadUtils.generateShards
@main
def main(args: String*): Unit = {
  ConfigManager.overrideWithArgs(args.toArray)
  val config = ConfigManager.getConfig
  val logger = LoggerFactory.getLogger(getClass)

  val originalGraphPath = config.getString("locations.originalGraph")
  val perturbedGraphPath = config.getString("locations.perturbedGraph")

  val shardDir = config.getString("locations.shardDir")
  val mapRedOutDir = config.getString("locations.mapRedOutDir")
  val groundTruthYAML = config.getString("locations.groundTruthYAML")
  val analysisOutputDir = config.getString("locations.analysisOutputDir")

  logger.info(s"originalGraphPath: $originalGraphPath")
  logger.info(s"perturbedGraphPath: $perturbedGraphPath")
  logger.info(s"Shard Directory: $shardDir")
  logger.info(s"MapReduce Output Directory: $mapRedOutDir")
  logger.info(s"Ground Truth YAML: $groundTruthYAML")
  logger.info(s"Analysis Output Directory: $analysisOutputDir")

  logger.info("Starting Graph Perturbation Predictor!")

  val nodeSizes = generateShards(config.getString("locations.originalGraph"), config.getString("locations.perturbedGraph"))

  logger.info(s"Number of nodes in original and perturbed graph: ${nodeSizes}")


  logger.info("Starting Map-Reduce job...")
  if(MapReduce.performMapReduce()){
    logger.info("Map-Reduce job completed successfully!")

    logger.info("Starting analyzing Map-Reduce output...")
    ProcessMapRedOut.performPostProcessing(nodeSizes)
    logger.info("Post processing finished!")
  }else{
    logger.error("Map-Reduce job did not complete!")
  }


}