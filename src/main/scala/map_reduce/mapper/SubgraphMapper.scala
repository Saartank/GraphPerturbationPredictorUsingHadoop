package map_reduce.mapper

import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*

import java.nio.file.{Files, Paths}
import scala.util.Using
import io.circe.parser.*
import org.apache.hadoop.io.Text
import org.apache.hadoop.mapreduce.Mapper
import org.apache.hadoop.mapreduce.lib.input.FileSplit
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

import load_utils.SubgraphShard
import load_utils.Shard
object SubgraphMapper {


  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)
  /*
  private val C: Double = 0.8 // Constant decay factor
  private val ITERATIONS: Int = 6 // Number of iterations to limit the recursion depth

  def simRank(original: SubgraphShard, perturbed: SubgraphShard): Map[Int, (Int, Double)] = {
    val memo = mutable.Map[(Int, Int, Int), Double]()

    def calculateSimilarity(a: Int, b: Int, iteration: Int): Double = {
      if (iteration == 0) return 0.0
      if (original.storedValues(a) == perturbed.storedValues(b)) return 1.0

      memo.get((a, b, iteration)) match {
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
  }*/

  class NodeMapper extends Mapper[Object, Text, Text, Text] {
    //val outputKey = new Text()
    //val outputValue = new IntWritable(1)

    val opKey = new Text()
    val opValue = new Text()

    override def map(key: Object, value: Text, context: Mapper[Object, Text, Text, Text]#Context): Unit = {

      val inputFileName = context.getInputSplit.asInstanceOf[FileSplit].getPath.getName
      val fileNameParts = inputFileName.toString.split("_")

      val result = decode[Shard](value.toString)
      result match {
        case Right(shard) =>

          //Adding structure encodings for Simrank

          val memo = mutable.Map[(Int, Int, Int), Double]()

          val orgGraphRanks = SimRank.simRank(shard.originalGraph, shard.perturbedGraph, memo)

          shard.originalGraph.subjectNodes.foreach { node_id =>
            val key: String = s"original_$node_id"
            val value : String =  s"${orgGraphRanks(node_id)(0)}_${orgGraphRanks(node_id)(1)}"
            opKey.set(key)
            opValue.set(value)
            context.write(opKey, opValue)
            if(key == "original_123"){
              logger.info(s"Written key : ${key}, value : ${value}")
            }
          }

          val perGraphRanks = SimRank.simRank(shard.perturbedGraph, shard.originalGraph, memo)

          shard.perturbedGraph.subjectNodes.foreach { node_id =>
            val key: String = s"perturbed_$node_id"
            val value: String = s"${perGraphRanks(node_id)(0)}_${perGraphRanks(node_id)(1)}"
            opKey.set(key)
            opValue.set(value)
            context.write(opKey, opValue)
            //logger.info(s"Written key : ${key}, value : ${value}")
          }

        case Left(error) =>
          logger.error("Failed to parse the Shard!")
      }
    }
  }


}
