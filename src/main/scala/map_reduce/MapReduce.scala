package map_reduce

import mapper.SubgraphMapper
import reducer.SubgraphReducer

import org.slf4j.{Logger, LoggerFactory}
import com.typesafe.config.ConfigFactory
import java.nio.file.{Files, Paths}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.Text
import org.apache.hadoop.mapreduce.Job
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat
import config_manager.ConfigManager
object MapReduce {
  val logger: Logger = LoggerFactory.getLogger(getClass)
  //private val config = ConfigFactory.load()
  private val config = ConfigManager.getConfig

  private val shardLocation = config.getString("locations.shardDir")
  private val mapRedOutLocation = config.getString("locations.mapRedOutDir")

  /*
  def validatePaths(): Boolean = {
    val dirPath = Paths.get(shardLocation)
    if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
      val dirStream = Files.list(dirPath)
      try {
        if (dirStream.findFirst().isPresent) {
          logger.info(s"The directory $shardLocation exists and is not empty.")
        } else {
          logger.warn(s"The directory $shardLocation exists but is empty.")
          return false
        }
      } catch {
        case e: Exception =>
          logger.error(s"An error occurred while checking the directory $shardLocation", e)
          return false
      } finally {
        dirStream.close()
      }
    } else {
      logger.error(s"The directory $shardLocation does not exist.")
    }
    val outputDirPath = Paths.get(mapRedOutLocation)
    if (Files.exists(outputDirPath)) {
      logger.error(s"The output directory $mapRedOutLocation already exists.")
      return false
    } else {
      logger.info(s"The output directory $mapRedOutLocation does not exist, safe to proceed.")
    }
    return true
  }*/
  def performMapReduce(): Boolean = {

    // removing because validation function fails in aws environment
    // if(validatePaths()) {
    
    if(true){
      val conf = new Configuration()

      logger.info(s"Setting speculation variable false...")
      conf.setBoolean("mapreduce.map.speculative", false)
      conf.setBoolean("mapreduce.reduce.speculative", false)

      logger.info(s"Setting reduce job 1...")
      conf.setInt("mapreduce.job.reduces", 1)
      val job = Job.getInstance(conf, "Node Analysis")

      job.setJarByClass(MapReduce.getClass)
      job.setMapperClass(classOf[SubgraphMapper.NodeMapper])
      job.setReducerClass(classOf[SubgraphReducer.NodeReducer])

      job.setOutputKeyClass(classOf[Text])
      job.setOutputValueClass(classOf[Text])

      FileInputFormat.addInputPath(job, new Path(shardLocation))
      FileOutputFormat.setOutputPath(job, new Path(mapRedOutLocation))

      //return job.waitForCompletion(true)
      logger.info(s"Map-Reduce job triggered!")
      val success: Boolean = job.waitForCompletion(true);
      return  success
    }
    logger.error(s"Not triggering Map-Reduce job!")
    return false
  }

  //For testing @main
  def main(): Unit = {
    val conf = new Configuration()
    //conf.set("mapreduce.job.reduces", "1")
    conf.setBoolean("mapreduce.map.speculative", false)
    conf.setBoolean("mapreduce.reduce.speculative", false)
    conf.setInt("mapreduce.job.reduces", 1)
    val job = Job.getInstance(conf, "Perturbation Predictor")

    job.setJarByClass(MapReduce.getClass)
    job.setMapperClass(classOf[SubgraphMapper.NodeMapper])
    job.setReducerClass(classOf[SubgraphReducer.NodeReducer])

    job.setOutputKeyClass(classOf[Text])
    job.setOutputValueClass(classOf[Text])

    FileInputFormat.addInputPath(job, new Path(shardLocation))
    FileOutputFormat.setOutputPath(job, new Path(mapRedOutLocation))

    val success: Boolean = job.waitForCompletion(true);
    println(s"success = ${success}")
    //System.exit(if (job.waitForCompletion(true)) 0 else 1)

  }

}
