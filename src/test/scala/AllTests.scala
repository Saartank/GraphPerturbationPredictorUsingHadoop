import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import load_utils.GraphLoadUtils.loadGraph
import post_map_reduce.utils.Utils.getScores
import post_map_reduce.utils.Utils.saveScores
import post_map_reduce.utils.Utils.createPlot
import NetGraphAlgebraDefs.NetGraph

import java.nio.file.{Files, Paths}
import config_manager.ConfigManager
import load_utils.SubgraphShard
import scala.collection.mutable
import map_reduce.mapper.SimRank.simRank



class SimRankSpec extends AnyFlatSpec with Matchers {

  "simRank" should "calculate the similarity between nodes of two graphs" in {
    val original = SubgraphShard(
      Set(1, 2, 3, 4),
      Map(
        1 -> List(2, 3),
        2 -> List(1, 3, 4),
        3 -> List(1, 2),
        4 -> List(2)
      ),
      Map(
        1 -> "A",
        2 -> "B",
        3 -> "C",
        4 -> "D"
      )
    )
    val perturbed = SubgraphShard(
      Set(5, 6, 7, 8),
      Map(
        5 -> List(6, 7),
        6 -> List(5, 7, 8),
        7 -> List(5, 6),
        8 -> List(6),
        9 -> List(8)
      ),
      Map(
        5 -> "A",
        6 -> "Y",
        7 -> "C",
        8 -> "D",
        9 -> "Z"
      )
    )

    val memo = mutable.Map[(Int, Int, Int), Double]()
    val similarities = simRank(original, perturbed, memo)

    // Assertion checks
    similarities(1)._2 should be(1.0)
    //similarities(2)._2 should be(1.0)
    similarities(3)._2 should be(1.0)
    similarities(4)._2 should be(1.0)
  }
}


class LoadGraphSpec extends AnyFlatSpec with Matchers {


  /* Works in IDE, but fails when I try to create a jar

  "loadGraph function" should "return a NetGraph object for a valid path" in {
    //val path = "src/test/resources/valid_test_graph.ngs"
    val resource = getClass.getClassLoader.getResource("valid_test_graph.ngs")
    val path = if (resource != null) resource.getPath else "path does not exist"
    val graph = loadGraph(path)

    graph should not be (None)     // Checking that the result is not None
    graph.get shouldBe a [NetGraph] // Checking that the result is a NetGraph instance
  }
  */
  it should "return None for an invalid path" in {
    val path = "src/test/resources/non-existent-file.ext"
    val graph = loadGraph(path)

    graph shouldBe None  // Checking that the result is None for an invalid path
  }
}

class GetScoresSpec extends AnyFlatSpec with Matchers {

  "getScores function" should "return Map(\"precision\" -> 1, \"recall\" -> 1, \"f1_score\" -> 1) when predicted and actual have same numbers" in {
    val predicted = List(1, 2, 3, 4, 5)
    val actual = List(5, 4, 3, 2, 1)

    val scores = getScores(predicted, actual, (predicted.length, actual.length))

    scores should contain("precision" -> 1.0)
    scores should contain("recall" -> 1.0)
    scores should contain("f1_score" -> 1.0)
  }

  it should "return Map(\"precision\" -> 0, \"recall\" -> 0, \"f1_score\" -> 0) when predicted and actual have completely different numbers" in {
    val predicted = List(1, 2, 3, 4, 5)
    val actual = List(6, 7, 8, 9, 10)

    val scores = getScores(predicted, actual, (predicted.length, actual.length))

    scores should contain("precision" -> 0.0)
    scores should contain("recall" -> 0.0)
    scores should contain("f1_score" -> 0.0)
  }
}

class SaveScoresSpec extends AnyFlatSpec with Matchers {

  "saveScores function" should "save the scores to a YAML file at the correct path" in {
    val config = ConfigManager.getConfig
    val analysisOutputDir = config.getString("locations.analysisOutputDir")

    // Corrected keys in the maps to "precision", "recall", and "f1_score"
    val modifyAcc = Map("precision" -> 0.9, "recall" -> 0.8, "f1_score" -> 0.85)
    val removeAcc = Map("precision" -> 0.7, "recall" -> 0.6, "f1_score" -> 0.65)
    val addAcc = Map("precision" -> 0.8, "recall" -> 0.7, "f1_score" -> 0.75)
    val tlBasedScores = Map("acc" -> 0.95, "btlr" -> 0.9, "vpr" -> 0.92)

    val dirPath = Paths.get(analysisOutputDir)
    if (!Files.exists(dirPath)) {
      Files.createDirectories(dirPath)
    }

    saveScores(modifyAcc, removeAcc, addAcc, tlBasedScores)
    val path = Paths.get(s"$analysisOutputDir/output_scores.yaml")
    Files.exists(path) should be (true)  // Check if the file is created

    Files.deleteIfExists(path)  // Clean up - delete the file
    Files.exists(path) should be (false)  // Confirm the file is deleted
  }
}


class CreatePlotSpec extends AnyFlatSpec with Matchers {

  "createPlot function" should "create a plot and save it as a PNG file at the correct path" in {
    val config = ConfigManager.getConfig
    val analysisOutputDir = config.getString("locations.analysisOutputDir")

    val modified = Map("precision" -> 1.0, "recall" -> 0.0, "f1_score" -> 1.0)
    val removed = Map("precision" -> 0.0, "recall" -> 0.5, "f1_score" -> 0.0)
    val added = Map("precision" -> 0.5, "recall" -> 0.5, "f1_score" -> 0.5)

    // Create the directory if it does not exist
    val dirPath = Paths.get(analysisOutputDir)
    if (!Files.exists(dirPath)) {
      Files.createDirectories(dirPath)
    }

    createPlot(modified, removed, added)  // Call the createPlot method

    val filePath = Paths.get(s"$analysisOutputDir/output_plot.png")
    Files.exists(filePath) should be (true)  // Check if the file is created

    Files.deleteIfExists(filePath)  // Clean up - delete the file
    Files.exists(filePath) should be (false)  // Confirm the file is deleted
  }
}