package map_reduce.mapper

import config_manager.ConfigManager

import scala.collection.mutable
import load_utils.SubgraphShard
private val config = ConfigManager.getConfig

object SimRank {

  private val C: Double = config.getString("parameters.simRankDecayFactor").toDouble
  private val ITERATIONS: Int = config.getString("parameters.simRankIterations").toInt

  def simRank(original: SubgraphShard, perturbed: SubgraphShard, memo: mutable.Map[(Int, Int, Int), Double]): Map[Int, (Int, Double)] = {

    def calculateSimilarity(a: Int, b: Int, iteration: Int): Double = {
      if (iteration == 0) return 0.0
      if (original.storedValues(a) == perturbed.storedValues(b)) return 1.0

      memo.get((a, b, iteration)).orElse(memo.get((b, a, iteration))) match {
        case Some(result) => return result
        case None =>
      }

      val neighborsA = original.adjList.getOrElse(a, List())
      val neighborsB = perturbed.adjList.getOrElse(b, List())

      if (neighborsA.isEmpty || neighborsB.isEmpty) return 0.0

      val similarities = for {
        neighborA <- neighborsA
        neighborB <- neighborsB
      } yield calculateSimilarity(neighborA, neighborB, iteration - 1)

      val result = C * similarities.sum / (neighborsA.size * neighborsB.size)
      memo += ((a, b, iteration) -> result)
      result
    }

    val results = for {
      nodeA <- original.adjList.keys
    } yield {
      val similarities = for {
        nodeB <- perturbed.adjList.keys
      } yield (nodeB, calculateSimilarity(nodeA, nodeB, ITERATIONS))

      val maxSimilarity = similarities.maxBy(_._2)
      (nodeA, maxSimilarity)
    }

    results.toMap
  }

}
