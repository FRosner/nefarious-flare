package de.frosner.nf

import org.apache.log4j.Logger

import scala.collection.mutable

object StageService {

  private val LOG = Logger.getLogger(this.getClass)

  private var stages: mutable.Map[Int, Stage] = mutable.HashMap.empty ++ Map(
    1 -> Stage(1, "Stage 1", "(in: org.apache.spark.sql.DataFrame) => in"),
    2 -> Stage(2, "Stage 2", "(in: org.apache.spark.sql.DataFrame) => \n  in.select(in(\"col1\").as(\"renamed\"))")
  )

  def getById(id: Int): Option[Stage] = {
    val stage = stages.get(id)
    LOG.info("Serving stage " + stage)
    stage
  }

  def updateById(id: Int, updated: Stage): Unit = {
    LOG.info("Updating stage " + updated)
    stages(id) = updated
  }

  def deleteById(id: Int): Boolean = {
    val maybeStage = stages.get(id)
    maybeStage.map(stage => {
      stages -= stage.id
      LOG.info("Removing stage " + stage)
      true
    }).getOrElse(false)
  }

  def getAll: Seq[(Int, Stage)] = {
    LOG.info(s"Getting all ${stages.size} stages")
    stages.toSeq
  }

}
