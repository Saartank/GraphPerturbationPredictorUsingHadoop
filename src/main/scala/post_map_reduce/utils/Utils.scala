package post_map_reduce.utils

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import org.jfree.chart.{ChartFactory, ChartUtils, JFreeChart}
import org.jfree.data.category.DefaultCategoryDataset

import scala.collection.mutable
import java.nio.file.{Paths, Files}
import scala.io.Source

import java.io.FileWriter
import org.yaml.snakeyaml.{DumperOptions, Yaml}

import java.util
//import scala.jdk.CollectionConverters.mapAsJavaMapConverter
import collection.JavaConverters.mapAsJavaMapConverter
import config_manager.ConfigManager
import AWS_utils.AWSUtils.{getS3File, writeS3file, writeS3string}
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
object Utils {
  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)
  //private val config = ConfigFactory.load()
  private val config = ConfigManager.getConfig
  private val mapRedOutput = config.getString("locations.mapRedOutDir")
  private val analysisOutputDir = config.getString("locations.analysisOutputDir")
  private val env = config.getString("locations.env")


  if (Files.notExists(Paths.get(analysisOutputDir))) {
    Files.createDirectories(Paths.get(analysisOutputDir))
    println(s"Directory $analysisOutputDir created.")
  } else {
    println(s"Directory $analysisOutputDir already exists.")
  }


  def createPlot(modified: Map[String, Double], removed: Map[String, Double], added: Map[String, Double]): Unit = {
    val dataset = new DefaultCategoryDataset()
    for ((nodeType, scores) <- Map("Modified" -> modified, "Removed" -> removed, "Added" -> added)) {
      for ((scoreType, score) <- scores) {
        dataset.addValue(score, scoreType, nodeType)
      }
    }

    // Create a bar chart
    val chart: JFreeChart = ChartFactory.createBarChart(
      "Scores for Node Perturbations",
      "Perturbation Type",
      "Score",
      dataset
    )

    val fileName = "output_plot.png"
    if (env.toLowerCase()!="aws") {
      val path = Paths.get(analysisOutputDir, fileName).toString
      // Save the chart to a file (in PNG format)
      ChartUtils.saveChartAsPNG(new java.io.File(path), chart, 800, 600)

      logger.info(s"Saved the plot file : ${path}.")
    }else{
      logger.info("Saving the chart in S3 bucket.")
      val path = s"$analysisOutputDir/$fileName"
      val os = new ByteArrayOutputStream()
      ChartUtils.writeChartAsPNG(os, chart, 900, 600)
      val input = new ByteArrayInputStream(os.toByteArray)

      writeS3file(os, input, path)
    }
  }

  /* Idea of TL changed

  def getScores(predicted: List[Int], actual: List[Int], nodeSizes: (Int,Int)): Map[String, Double] = {

    logger.info(s"Predicted: ${predicted}")
    logger.info(s"Actual: ${actual}")

    val atl = predicted.count(a => actual.contains(a))
    val ctl = actual.count(a => !predicted.contains(a))
    val wtl = predicted.length - atl
    //val dtl = 2400 - (atl + ctl + wtl)
    val dtl = (nodeSizes(0) + nodeSizes(1) - (2*atl + ctl + wtl))/2

    val btl = ctl + wtl
    val gtl = atl + dtl
    val rtl = gtl + btl

    val acc = atl.toDouble / rtl
    val btlr = wtl.toDouble / rtl
    val vpr = (gtl - btl).toDouble / (2 * rtl) + 0.5
    logger.info(s"dtl : ${dtl}, atl : ${atl}, ctl : ${ctl}, wtl : ${wtl}")
    logger.info(f"acc: ${acc}%.4f, btlr: ${btlr}%.4f, vpr: ${vpr}%.4f")
    Map("acc" -> acc, "btlr" -> btlr, "vpr" -> vpr)
  }*/

  def getScores(predicted: List[Int], actual: List[Int], nodeSizes: (Int, Int)): Map[String, Double] = {

    logger.info(s"Predicted: ${predicted}")
    logger.info(s"Actual: ${actual}")

    val tp = predicted.count(a => actual.contains(a))
    val fn = actual.count(a => !predicted.contains(a))
    val fp = predicted.length - tp
    //val dtl = 2400 - (atl + ctl + wtl)
    val tn = (nodeSizes(0) + nodeSizes(1) - (2 * tp + fn + fp)) / 2

    val precision: Double = if (tp+fp>0) tp.toDouble / (tp.toDouble + fp.toDouble) else 0.0
    val recall: Double = if (tp+fp>0) tp.toDouble / (tp.toDouble + fn.toDouble) else 0.0

    val f1_score = if(precision+recall>0.0) 2 * ((precision*recall)/(precision+recall)) else 0.0

    logger.info(s"True-Negatives : ${tn}, True-Positives : ${tp}, False-Negatives : ${fn}, False-Positives : ${fp}")
    logger.info(f"precision -> ${precision}%.4f recall -> ${recall}%.4f, f1_score -> ${f1_score}%.4f")
    Map("precision" -> precision, "recall" -> recall, "f1_score" -> f1_score)
  }

  def getTlBasedScores(modifiedActual: List[Int], modifiedPredicted: List[Int], removedActual: List[Int], removedPredicted: List[Int], addedActual: List[Int], addedPredicted: List[Int], nodeSizes: (Int, Int)) : Map[String, Double]={
    val changedActual = removedActual ++ addedActual
    val changedPredicted = removedPredicted ++ removedPredicted
    val dtl = changedActual.count(a => changedPredicted.contains(a))
    val extraChanged = changedPredicted.size - dtl
    val notIncludedChanged = changedActual.size - dtl


    val modifiedRight = modifiedActual.count(a => modifiedPredicted.contains(a))
    val extraModified = modifiedPredicted.size - modifiedRight
    val notIncludedModified = modifiedActual.size - modifiedRight


    val unChangedActual = nodeSizes(0) - changedActual.size - notIncludedModified
    val unChangedPredicted = nodeSizes(1) - changedPredicted.size - extraModified

    logger.info(s"unChangedActual : $unChangedActual")
    logger.info(s"unChangedPredicted: $unChangedPredicted")
    val atl = modifiedRight +  (unChangedActual + unChangedPredicted)/2

    val ctl = notIncludedModified + extraChanged
    val wtl = extraModified + notIncludedChanged

    val btl = ctl + wtl
    val gtl = atl + dtl
    val rtl = gtl + btl

    val acc = atl.toDouble / rtl
    val btlr = wtl.toDouble / rtl
    val vpr = (gtl - btl).toDouble / (2 * rtl) + 0.5
    logger.info(s"dtl : ${dtl}, atl : ${atl}, ctl : ${ctl}, wtl : ${wtl}")
    logger.info(f"acc: ${acc}%.4f, btlr: ${btlr}%.4f, vpr: ${vpr}%.4f")
    Map("acc" -> acc, "btlr" -> btlr, "vpr" -> vpr)

  }
  def oldgetTlBasedScores(actualAllChanged : List[Int], predictedAllChanged : List[Int], nodeSizes: (Int, Int)) : Map[String, Double]={
    val dtl = actualAllChanged.count(a => predictedAllChanged.contains(a))
    val ctl = actualAllChanged.size - dtl
    val wtl = predictedAllChanged.size - dtl
    val atl = (nodeSizes(0) + nodeSizes(1) - ctl - wtl - 2*dtl)/2

    val gtl = atl + dtl
    val btl = wtl + ctl

    val rtl = gtl + btl

    val acc = atl.toDouble / rtl
    val btlr = wtl.toDouble / rtl
    val vpr = (gtl - btl).toDouble / (2 * rtl) + 0.5

    logger.info(s"dtl : ${dtl}, atl : ${atl}, ctl : ${ctl}, wtl : ${wtl}")
    logger.info(f"acc: ${acc}%.4f, btlr: ${btlr}%.4f, vpr: ${vpr}%.4f")
    Map("acc" -> acc, "btlr" -> btlr, "vpr" -> vpr)
  }


  def splitUsingKMeans(modified_removed_buffer: mutable.ListBuffer[(Int, Double)]) : (mutable.ListBuffer[Int], mutable.ListBuffer[Int]) ={
    val removed = mutable.ListBuffer[Int]()
    val modified = mutable.ListBuffer[Int]()

    if (modified_removed_buffer.nonEmpty) {
      var centroids = modified_removed_buffer.map(_._2).distinct.take(2).toList // Taking two distinct simScores as initial centroids

      var clusters = Map.empty[Double, mutable.ListBuffer[(Int, Double)]]

      var oldCentroids = List.empty[Double]
      val maxIterations = 100 // Maximum number of iterations to avoid infinite loop
      var iteration = 0

      while (oldCentroids != centroids && iteration < maxIterations) {
        oldCentroids = centroids

        // Assign each node_id to the closest centroid
        clusters = modified_removed_buffer.groupBy { case (_, simScore) =>
          centroids.minBy(centroid => math.abs(centroid - simScore))
        }

        // Recompute centroids
        centroids = clusters.keys.toList

        iteration += 1
      }

      val (lowerCluster, higherCluster) = clusters.keys.toList match {
        case List(c1, c2) if c1 < c2 => (c1, c2)
        case List(c1, c2) => (c2, c1)
        case _ => throw new RuntimeException("Couldn't determine clusters.")
      }

      modified_removed_buffer.foreach { case (node_id, simScore) =>
        if (math.abs(simScore - lowerCluster) < math.abs(simScore - higherCluster)) {
          removed += node_id
        } else {
          modified += node_id
        }
      }
    }

    return (modified, removed)
  }

  def readMapRedOut(): (List[Int], List[Int], List[Int])  = {
    logger.info("Parsing Map-Reduce output file...")

    val path = s"${mapRedOutput}/part-r-00000"

    val file = if (env.toLowerCase() != "aws") then {
      logger.info("Environment not AWS, reading a local file.")
      logger.info(s"Attempting to access the file: ${path}")
      scala.io.Source.fromFile(path)
    }else{
      logger.info("Environment is AWS, reading a file in S3.")
      logger.info(s"Attempting to access the file: ${path}")
      getS3File(path)
    }

    val added = mutable.ListBuffer[Int]()
    val added_buffer = mutable.ListBuffer[Int]()

    val modified_removed_buffer = mutable.ListBuffer[(Int, Double)]()


    for (line <- file.getLines()) {

      val parts = line.split('\t')
      if (parts.length == 2) {
        val nodeLabel = parts(0)
        val simRankOp = parts(1)

        val nodeLabelParts = nodeLabel.split('_')
        val simRankOpParts = simRankOp.split('_')

        if (nodeLabelParts.length == 2 && simRankOpParts.length == 2) {
          val label = nodeLabelParts(0)
          val node_id = nodeLabelParts(1)
          val other_graph_node_id = simRankOpParts(0)
          val simScore = simRankOpParts(1)

          if (label == "original") {
            modified_removed_buffer.append((node_id.toInt, simScore.toDouble))
          } else {
            added_buffer.append(node_id.toInt)
          }

        } else {
          logger.info("Invalid Map-Reduce output format: Each part after splitting by tab or underscore should have exactly two elements.")
        }
      } else {
        println("Invalid Map-Reduce format: Line should contain exactly one tab character.")
      }


    }

    file.close()

    logger.info("Finished reading Map-Reduce output!")
    logger.info("Starting K-Means algorithm to found a boundary between modified and removed node.")
    val (modified, removed) = splitUsingKMeans(modified_removed_buffer)
    logger.info("K-Means algorithm finished.")

    val toBeAdded = added_buffer.toSet -- modified.toSet
    added ++= toBeAdded



    // Convert the ListBuffers to Lists if needed
    val modifiedPredicted = modified.toList
    val removedPredicted = removed.toList
    val addedPredicted = added.toList

    logger.info(s"Modified : $modifiedPredicted")
    logger.info(s"Removed : $removedPredicted")
    logger.info(s"Added : $addedPredicted")

    (modifiedPredicted, removedPredicted, addedPredicted)
  }

  def saveScores(modifyAcc: Map[String, Double], removeAcc: Map[String, Double], addAcc: Map[String, Double], tlBasedScores: Map[String, Double]): Unit = {

    def formatDouble(value: Double): Double =
      BigDecimal(value).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble

    val data = Map(
      "Traceability-link based scores: " -> Map(
        "acc" -> formatDouble(tlBasedScores("acc")),
        "btlr" -> formatDouble(tlBasedScores("btlr")),
        "vpr" -> formatDouble(tlBasedScores("vpr"))
      ).asJava,
      "Confusion-matrix based scores: " -> Map(
        "Modified Nodes" -> Map(
          "Precision" -> formatDouble(modifyAcc("precision")),
          "Recall" -> formatDouble(modifyAcc("recall")),
          "F-1 Score" -> formatDouble(modifyAcc("f1_score"))
        ).asJava,
        "Added Nodes" -> Map(
          "Precision" -> formatDouble(addAcc("precision")),
          "Recall" -> formatDouble(addAcc("recall")),
          "F-1 Score" -> formatDouble(addAcc("f1_score"))
        ).asJava,
        "Removed Nodes" -> Map(
          "Precision" -> formatDouble(removeAcc("precision")),
          "Recall" -> formatDouble(removeAcc("recall")),
          "F-1 Score" -> formatDouble(removeAcc("f1_score"))
        ).asJava
      ).asJava
    ).asJava

    val fileName = "output_scores.yaml"
    if (env.toLowerCase()!="aws") {
      val path = Paths.get(analysisOutputDir, fileName).toString
      val writer = new FileWriter(path)
      val options = new DumperOptions()

      options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
      options.setPrettyFlow(true)
      val yaml = new Yaml(options)

      try {
        yaml.dump(data, writer)
        logger.info(s"Saved the YAML file containing scores: ${path}.")

      } finally {
        writer.close()
      }
    }else{
      val path = s"$analysisOutputDir/$fileName"
      logger.info(s"Environment is AWS, saving yaml file in S3: $path")
      val options = new DumperOptions()
      options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
      options.setPrettyFlow(true)

      val yaml = new Yaml(options)
      val yamlString = yaml.dump(data)

      writeS3string(yamlString, path)
    }
  }
}
