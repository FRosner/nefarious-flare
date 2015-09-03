package de.frosner.nf

import com.twitter.util.Eval
import org.apache.log4j.Logger
import org.apache.spark.sql.types.{IntegerType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable

object ExecutionService {

  private val LOG = Logger.getLogger(this.getClass)

  private val conf = new SparkConf().setMaster("local[*]").setAppName("Test")
  private val sc = new SparkContext(conf)
  private val sql = new SQLContext(sc)

  private val startDf = {
    val rdd = sc.parallelize(List(Row(1), Row(2), Row(3)))
    val schema = StructType(List(StructField("col1", IntegerType, false)))
    sql.createDataFrame(rdd, schema)
  }

  private val results: mutable.Map[Int, ExecutionResult] = mutable.HashMap.empty

  private val eval = new Eval(None)

  private def compile[T](code: String): T = eval[T](code)

  private def compileTransformation(code: String): DataFrame => DataFrame = compile(code)

  def run(stage: Stage): ExecutionResult = {
    // TODO do the execution asynchronously
    LOG.info(s"Starting execution of stage $stage")
    val stageTransformation = compileTransformation(stage.code)
    val resultDf = stageTransformation(startDf)
    val result = ExecutionResult(results.size, stage.id, resultDf.collect().mkString("\n"))
    results(result.id) = result
    result
  }

}
