package load_utils

import org.slf4j.LoggerFactory

import scala.collection.mutable
import NetGraphAlgebraDefs.NetGraph
import config_manager.ConfigManager

import scala.jdk.CollectionConverters.*
import scala.util.Using
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*

import java.nio.file.{Files, Paths}

import AWS_utils.AWSUtils.writeS3string

case class SubgraphShard(subjectNodes: Set[Int], adjList: Map[Int, List[(Int)]], storedValues: Map[Int, String])
import scala.collection.mutable.ArrayBuffer

case class Shard(originalGraph: SubgraphShard, perturbedGraph: SubgraphShard)
object GraphUtils {
  private val logger = LoggerFactory.getLogger(classOf[GraphUtils.type])
  private val config = ConfigManager.getConfig
  private val shardDir = config.getString("locations.shardDir")
  private val numberOfShards = config.getString("parameters.numOfGraphDivisions")
  private val env = config.getString("locations.env")
  private val extraHops: Int = config.getString("parameters.shardingExtraHops").toInt


  def extractSubgraph(
                       adjacencyList: Map[Int, List[Int]],
                       nodes: Set[Int],
                       maxHop: Int = extraHops
                     ): Map[Int, List[Int]] = {

    // Helper function to recursively explore the graph and include neighbors
    def explore(node: Int, hop: Int = 0, visited: Set[Int] = Set.empty[Int]): Set[Int] = {
      if (hop <= maxHop && !visited.contains(node)) {
        val neighbors = adjacencyList.getOrElse(node, List.empty)
        val newVisited = neighbors.foldLeft(visited + node) {
          (accVisited, neighbor) => explore(neighbor, hop + 1, accVisited)
        }
        return newVisited
      } else {
        return visited
      }
    }

    val nodeSetList = nodes.map { element =>
      explore(element)
    }

    val subgraphNodes = nodeSetList.reduce(_ union _)
    val subgraph = subgraphNodes.map { node =>
      node -> adjacencyList.getOrElse(node, List.empty).intersect(subgraphNodes.toList)
    }.toMap

    subgraph
  }

  def partitionGraph(adjList: Map[Int, List[Int]], numOfParts: Int): Array[Set[Int]] = {
    val vertices = adjList.keys.toSeq
    val partitions = Array.fill(numOfParts)(Set.empty[Int])
    val visited = mutable.Set.empty[Int]

    def dfs(vertex: Int, smallestPartitionIndex: Int): Unit = {
      if (!visited(vertex) && partitions(smallestPartitionIndex).size < vertices.size / numOfParts) {
        partitions(smallestPartitionIndex) += vertex
        visited += vertex
        //println(s"added $vertex in $smallestPartitionIndex by dfs")
        adjList.getOrElse(vertex, Nil).foreach(nei => dfs(nei, smallestPartitionIndex))
      }
    }

    vertices.foreach { vertex =>
      if (!visited(vertex)) {
        val smallestPartitionIndex = partitions.zipWithIndex.minBy(_._1.size)._2
        partitions(smallestPartitionIndex) += vertex
        visited += vertex
        //println(s"added $vertex in $smallestPartitionIndex by while loop")
        adjList.getOrElse(vertex, Nil).foreach(nei => dfs(nei, smallestPartitionIndex))
      }
    }

    partitions
  }



  def convertToUndirectedGraph(adjacencyList: Map[Int, List[Int]]): Map[Int, List[Int]] = {
    adjacencyList.foldLeft(Map.empty[Int, List[Int]]) { case (undirectedGraph, (source, neighbors)) =>
      neighbors.foldLeft(undirectedGraph) { (graph, neighbor) =>
        // Add the edge from source to neighbor
        val updatedGraph1 = graph + (source -> (neighbor :: graph.getOrElse(source, Nil)))
        // Add the edge from neighbor to source
        updatedGraph1 + (neighbor -> (source :: updatedGraph1.getOrElse(neighbor, Nil)))
      }
    }
  }

  def getSubGraphShard(someGraph: NetGraph): ArrayBuffer[SubgraphShard] = {
    val numParts = numberOfShards.toInt
    //Preparing the original graph
    val allNodes = someGraph.sm.nodes.asScala.toList
    val edgeList = someGraph.sm.edges.asScala.toList.map { element =>
      element.asScala.toList
    }

    val adjacencyList: Map[Int, List[Int]] = allNodes.map { node =>
      val allSrcs = edgeList.collect {
        case innerList if innerList(0).id == node.id => innerList(1).id
      }
      node.id -> allSrcs
    }.toMap

    val undirAdjacencyList = GraphUtils.convertToUndirectedGraph(adjacencyList)

    val splitNodes :Array[Set[Int]] = partitionGraph(undirAdjacencyList, numParts)

    val shards = ArrayBuffer[SubgraphShard]()

    for (i <- 1 to splitNodes.length) {
      val nodeIds = splitNodes(i - 1)

      val subAdjList: Map[Int, List[Int]] = GraphUtils.extractSubgraph(undirAdjacencyList, nodeIds)

      def findStoredValueById(idToFind: Int): String = {
        val matchingNode = allNodes.find(_.id == idToFind)
        matchingNode match {
          case Some(node) => node.storedValue.toString
          case None => throw new NoSuchElementException(s"Custom exception no node found with id $idToFind")
        }
      }

      val stored_values = subAdjList.map { case (key, value) =>
        key -> findStoredValueById(key)
      }

      val missingKeys = nodeIds.filterNot(subAdjList.contains)


      val shard = new SubgraphShard(nodeIds, subAdjList, stored_values)
      shards+=shard
    }

    shards
  }

  def saveShards(originalGraph: NetGraph, perturbedGraph: NetGraph): Unit = {
    val orgShards = getSubGraphShard(originalGraph)
    val perShards = getSubGraphShard(perturbedGraph)

    for {
      (shard1, index1) <- orgShards.zipWithIndex
      (shard2, index2) <- perShards.zipWithIndex
    } {
      val uniqueIdentifier = s"$index1-$index2"
      val shard = Shard(shard1, shard2)

      val jsonString = shard.asJson.noSpaces

      val filePath = s"$shardDir/subgraph_shard_${uniqueIdentifier}.json"

      if (env.toLowerCase() != "aws") {
        Using(Files.newBufferedWriter(Paths.get(filePath))) { writer =>
          writer.write(jsonString)
        }.getOrElse(println(s"Failed to write JSON to file: $filePath"))
      } else {
        writeS3string(jsonString, filePath)
      }
      logger.info(s"Saved shard with uniqueIdentifier ${uniqueIdentifier}")
    }
  }


}
