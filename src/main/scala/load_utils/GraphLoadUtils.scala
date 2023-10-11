package load_utils

import NetGraphAlgebraDefs.*
import config_manager.ConfigManager
import org.slf4j.LoggerFactory
import AWS_utils.AWSUtils.getS3FileAsInputStream

import java.io.{BufferedInputStream, ObjectInputStream, FileInputStream}
import scala.io.BufferedSource
import scala.util.Try
import java.nio.file.{Paths, Files}


object GraphLoadUtils {
  private val logger = LoggerFactory.getLogger(classOf[GraphLoadUtils.type])
  private val config = ConfigManager.getConfig
  private val env = config.getString("locations.env")
  private val shardDir = config.getString("locations.shardDir")



  def loadGraph(path: String): Option[NetGraph]={

    // Load the graph in a Netgraph object
    logger.info(s"Loading the NetGraph from $path")

    Try {
      val inputStream = if (env.toLowerCase()!="aws") then new FileInputStream(path) else getS3FileAsInputStream(path)
      val ois = new ObjectInputStream(inputStream)

      val ng = ois.readObject().asInstanceOf[List[NetGraphComponent]]
      logger.info(s"Deserialized the object $ng")

      ois.close()
      inputStream.close()

      ng
    }.toOption.flatMap { listOfNetComponents =>
      val nodes = listOfNetComponents.collect { case node: NodeObject => node }
      val edges = listOfNetComponents.collect { case edge: Action => edge }
      logger.info(s"Deserialized ${nodes.length} nodes and ${edges.length} edges")

      // returns a Netgraph object
      NetModelAlgebra(nodes, edges)
    }

  }

  def generateShards(originalGraphPath: String, perturbedGraphPath: String): (Int, Int) ={

    // Create shard dir if it does not already exist, this only works in the local environment and not in aws.
    if (Files.notExists(Paths.get(shardDir))) {
      Files.createDirectories(Paths.get(shardDir))
      println(s"Directory $shardDir created.")
    } else {
      println(s"Directory $shardDir already exists.")
    }
    logger.info("Generating shards...")
    val originalGraph = loadGraph(originalGraphPath)
    val perturbedGraph = loadGraph(perturbedGraphPath)

    GraphUtils.saveShards(originalGraph.get, perturbedGraph.get)

    logger.info(s"Shards successfully generated at: $shardDir")

    val orgNodes = originalGraph.get.sm.nodes().size()
    val perNodes = perturbedGraph.get.sm.nodes().size()

    (orgNodes, perNodes)
  }

}
