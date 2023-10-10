package post_map_reduce

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml

import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.io.Source
import utils.Utils
import scala.util.Try

import java.nio.file.{Files, Paths}
import config_manager.ConfigManager
import AWS_utils.AWSUtils.getS3File

object ProcessMapRedOut {
  val logger = LoggerFactory.getLogger(getClass)
  //val config = ConfigFactory.load()
  private val config = ConfigManager.getConfig
  private val env = config.getString("locations.env")
  private val yamlGT = config.getString("locations.groundTruthYAML")
  private val analysisOutputDIr = config.getString("locations.analysisOutputDir")
  def performPostProcessing(nodeSizes: (Int, Int)): Unit = {
    val (modifiedPredicted, removedPredicted, addedPredicted) : (List[Int], List[Int], List[Int]) = Utils.readMapRedOut()

    val fileContent = Try {
      val source = if (env.toLowerCase()!="aws") then Source.fromFile(yamlGT) else getS3File(yamlGT)
      val content = source.mkString.replaceAll("\t", " ") // Replace tabs with spaces
      source.close()
      content
    }.getOrElse("")

    if (fileContent ==""){
      logger.error(s"Not able to parse yaml file : ${yamlGT}")
    }

    logger.info("Finished parsing the Map-Reduce output file.")

    val data = new Yaml().load(fileContent).asInstanceOf[java.util.Map[String, Any]].asScala.toMap
    data.get("Nodes") match {
      case Some(nodes) => nodes.asInstanceOf[java.util.Map[String, Any]].asScala.toMap match {
        case nodeMap: Map[String, Any] =>
          val modifiedActual = nodeMap("Modified").asInstanceOf[java.util.ArrayList[Int]].asScala.toList
          val removedActual = nodeMap("Removed").asInstanceOf[java.util.ArrayList[Int]].asScala.toList
          val addedActual = nodeMap("Added").asInstanceOf[java.util.LinkedHashMap[Int, Int]].asScala.toMap.values.toList

          logger.info("Calculating traceability-link based...")

          val tlBasedScores = Utils.getTlBasedScores(modifiedActual, modifiedPredicted, removedActual, removedPredicted, addedActual, addedPredicted, nodeSizes)


          logger.info("Calculating confusion-matrix based scores for 'Modified Nodes'...")
          val modifyAcc = Utils.getScores(modifiedPredicted, modifiedActual, nodeSizes)
          logger.info("Calculating confusion-matrix based scores for 'Removed Nodes'...")
          val removeAcc = Utils.getScores(removedPredicted, removedActual, nodeSizes)
          logger.info("Calculating confusion-matrix based scores for 'Added Nodes'...")
          val addAcc = Utils.getScores(addedPredicted, addedActual, nodeSizes)

          Utils.createPlot(modifyAcc, removeAcc, addAcc)
          Utils.saveScores(modifyAcc, removeAcc, addAcc, tlBasedScores)
      }
      case _ => logger.error("'Nodes' not found in the data")
    }

  }
}
