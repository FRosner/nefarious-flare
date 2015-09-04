package de.frosner.nf

import com.twitter.util.Eval
import org.apache.log4j.Logger
import org.apache.spark.sql.types.{IntegerType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import scala.concurrent.ExecutionContext.Implicits.global

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

  private val results: mutable.Map[Int, Option[Try[String]]] = mutable.HashMap.empty

  private val eval = new Eval(None)

  private def compile[T](code: String): T = eval[T](code)

  private def compileTransformation(code: String): DataFrame => DataFrame = compile(code)

  def init = {}

  def createFromStage(stage: Stage): Try[Int] = {
    // TODO do the execution asynchronously (by sending this stage to an actor)?
    LOG.info(s"Starting execution of stage $stage")
    val maybeStageTransformation = Try(compileTransformation(stage.code))
    
    maybeStageTransformation.map(t => {
      val newResultId = results.size
      results(newResultId) = None
      val result = Future { t(startDf) }.onComplete{ df =>
        results(newResultId) = Some(df.map(_.take(10).mkString("\n")))
      }
      newResultId
    })
  }

  def getById(id: Int): Option[Option[Try[String]]] = {
    results.get(id)
  }

}
