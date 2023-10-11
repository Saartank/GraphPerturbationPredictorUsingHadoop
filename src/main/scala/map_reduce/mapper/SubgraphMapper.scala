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

  class NodeMapper extends Mapper[Object, Text, Text, Text] {
    val opKey = new Text()
    val opValue = new Text()

    override def map(key: Object, value: Text, context: Mapper[Object, Text, Text, Text]#Context): Unit = {

      val inputFileName = context.getInputSplit.asInstanceOf[FileSplit].getPath.getName
      val fileNameParts = inputFileName.toString.split("_")

      val result = decode[Shard](value.toString)
      result match {
        case Right(shard) =>
          
          val memo = mutable.Map[(Int, Int, Int), Double]()

          val orgGraphRanks = SimRank.simRank(shard.originalGraph, shard.perturbedGraph, memo)

          shard.originalGraph.subjectNodes.foreach { node_id =>
            val key: String = s"original_$node_id"
            val value : String =  s"${orgGraphRanks(node_id)(0)}_${orgGraphRanks(node_id)(1)}"
            opKey.set(key)
            opValue.set(value)
            context.write(opKey, opValue)
            
          }

          val perGraphRanks = SimRank.simRank(shard.perturbedGraph, shard.originalGraph, memo)

          shard.perturbedGraph.subjectNodes.foreach { node_id =>
            val key: String = s"perturbed_$node_id"
            val value: String = s"${perGraphRanks(node_id)(0)}_${perGraphRanks(node_id)(1)}"
            opKey.set(key)
            opValue.set(value)
            context.write(opKey, opValue)
          }

        case Left(error) =>
          logger.error("Failed to parse the Shard!")
      }
    }
  }


}
