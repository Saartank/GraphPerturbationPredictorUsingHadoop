package map_reduce.reducer

import org.apache.hadoop.io.Text
import org.apache.hadoop.mapreduce.Reducer
import collection.JavaConverters.iterableAsScalaIterableConverter
import org.slf4j.LoggerFactory

object SubgraphReducer {
  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  class NodeReducer extends Reducer[Text, Text, Text, Text] {
    override def reduce(key: Text, values: java.lang.Iterable[Text], context: Reducer[Text, Text, Text, Text]#Context): Unit = {

      /*
      if(key.toString == "original_123"){
        val strings: Iterable[String] = values.asScala.map(_.toString)
        val result: String = strings.mkString("|")
        logger.info(s"From Reducer: key ${key}, values ${result}")
      }*/

      /*
      val valueList = values.asScala.toList

      val idScorePairs = valueList.map { item =>
        val Array(id, score) = item.toString.split("_")
        (id, score.toDouble)
      }

      val maxScorePair = idScorePairs.maxBy(_._2)
      val newValue = s"${maxScorePair(0)}_${maxScorePair(1)}"

      if (maxScorePair(1) < 1){
        context.write(key, new Text(newValue))
      }*/

      val valueList = values.asScala.map(_.toString).toList

      val idScorePairs = valueList.map { item =>
        val Array(id, score) = item.split("_")
        (id, score.toDouble)
      }

      val maxScorePair = if (idScorePairs.nonEmpty) {
        Some(idScorePairs.maxBy(_._2))
      } else {
        None
      }

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

