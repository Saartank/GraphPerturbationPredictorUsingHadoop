package map_reduce.reducer

import org.apache.hadoop.io.Text
import org.apache.hadoop.mapreduce.Reducer
import collection.JavaConverters.iterableAsScalaIterableConverter
import org.slf4j.LoggerFactory

object SubgraphReducer {
  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  class NodeReducer extends Reducer[Text, Text, Text, Text] {
    override def reduce(key: Text, values: java.lang.Iterable[Text], context: Reducer[Text, Text, Text, Text]#Context): Unit = {


      //Storing mapper output as this data can be only accessed once
      val valueList = values.asScala.map(_.toString).toList

      val idScorePairs = valueList.map { item =>
        val Array(id, score) = item.split("_")
        (id, score.toDouble)
      }

      //Finding value corresponding to the max SimScore
      val maxScorePair = if (idScorePairs.nonEmpty) {
        Some(idScorePairs.maxBy(_._2))
      } else {
        None
      }

      //Broadcast key-value pairs if a perfect match is not found
      maxScorePair match {
        case Some(pair) => {
          val newValue = s"${pair(0)}_${pair(1)}"

          if (pair(1) < 1) {
            context.write(key, new Text(newValue))
          }
        }
        case None => logger.info("Something went wrong, values seem to be missing!!")
      }
    }
  }

}

