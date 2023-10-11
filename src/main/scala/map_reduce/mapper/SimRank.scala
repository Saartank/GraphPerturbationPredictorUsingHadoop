package map_reduce.mapper

import config_manager.ConfigManager

import scala.collection.mutable
import load_utils.SubgraphShard
private val config = ConfigManager.getConfig

object SimRank {
  // Configuration parameters for the SimRank algorithm
  private val C: Double = config.getString("parameters.simRankDecayFactor").toDouble
  private val ITERATIONS: Int = config.getString("parameters.simRankIterations").toInt

  // Function to calculate the SimRank similarity between two subgraph shards
  def simRank(original: SubgraphShard, perturbed: SubgraphShard, memo: mutable.Map[(Int, Int, Int), Double]): Map[Int, (Int, Double)] = {

    // Nested function to calculate similarity recursively with memoization
    def calculateSimilarity(a: Int, b: Int, iteration: Int): Double = {

      // Base cases: If iteration is 0, or both nodes have the same value
      if (iteration == 0) return 0.0
      if (original.storedValues(a) == perturbed.storedValues(b)) return 1.0
      // Check if the result is already calculated to avoid recomputation
      memo.get((a, b, iteration)).orElse(memo.get((b, a, iteration))) match {
        case Some(result) => return result
        case None =>
      }
      // Get the neighbors of the given nodes
      val neighborsA = original.adjList.getOrElse(a, List())
      val neighborsB = perturbed.adjList.getOrElse(b, List())

      // If either node has no neighbors, return 0.0 similarity
      if (neighborsA.isEmpty || neighborsB.isEmpty) return 0.0
      // Calculate similarities for all pairs of neighbors
      val similarities = for {
        neighborA <- neighborsA
        neighborB <- neighborsB
      } yield calculateSimilarity(neighborA, neighborB, iteration - 1)

      val result = C * similarities.sum / (neighborsA.size * neighborsB.size)
      memo += ((a, b, iteration) -> result)
      result
      // Return the final similarity score

    }

    val results = for {
      nodeA <- original.adjList.keys // Iterate over each node in the original graph
    } yield {
      val similarities = for {
        nodeB <- perturbed.adjList.keys
      } yield (nodeB, calculateSimilarity(nodeA, nodeB, ITERATIONS))

      val maxSimilarity = similarities.maxBy(_._2)
      (nodeA, maxSimilarity) // Return the node and the max similarity score
    }

    results.toMap
  }

}
