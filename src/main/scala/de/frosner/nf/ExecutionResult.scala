package de.frosner.nf

import spray.json.DefaultJsonProtocol

case class ExecutionResult(id: Int, stageId: Int, outcome: String)

object ExecutionResultJsonProtocol extends DefaultJsonProtocol {
  implicit val executionResultFormat = jsonFormat3(ExecutionResult.apply)
}
