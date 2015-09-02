package de.frosner.nf

import spray.json.DefaultJsonProtocol

case class Stage(id: Int, name: String, code: String) {

}

object StageJsonProtocol extends DefaultJsonProtocol {
  implicit val stageFormat = jsonFormat3(Stage.apply)
}
