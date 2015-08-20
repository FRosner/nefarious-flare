package de.frosner.nf

import com.twitter.util.Eval
import org.apache.spark.sql.types.{IntegerType, StructField, StructType}
import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.sql.{SQLContext, DataFrame, Row}

object SparkRunner {

  private val conf = new SparkConf().setMaster("local[*]").setAppName("Test")
  private val sc = new SparkContext(conf)
  private val sql = new SQLContext(sc)

  private val startDf = {
    val rdd = sc.parallelize(List(Row(1), Row(2), Row(3)))
    val schema = StructType(List(StructField("col1", IntegerType, false)))
    sql.createDataFrame(rdd, schema)
  }

  private val eval = new Eval(None)

  private def compile[T](code: String): T = eval[T](code)

  private def compileTransformation(code: String): DataFrame => DataFrame = compile(code)

  def run(stage: Stage) = {
    val stageTransformation = compileTransformation(stage.code)
    stageTransformation(startDf).show()
  }

}
