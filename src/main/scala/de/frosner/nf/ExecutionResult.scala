package de.frosner.nf

import de.frosner.nf.StageJsonProtocol._
import spray.json.DefaultJsonProtocol

import scala.util.Try

case class ExecutionResult(id: Int, stageId: Int, outcome: String)

object ExecutionResultJsonProtocol extends DefaultJsonProtocol {
  implicit val executionResultFormat = jsonFormat3(ExecutionResult.apply)
}
