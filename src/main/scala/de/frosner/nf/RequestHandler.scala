package de.frosner.nf

import akka.actor._
import de.frosner.nf.ExecutionResultJsonProtocol.executionResultFormat
import de.frosner.nf.RequestHandler._
import de.frosner.nf.StageJsonProtocol.stageFormat
import spray.http.MediaTypes._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json.{JsArray, JsNumber}
import spray.routing._
import spray.routing.authentication.{BasicHttpAuthenticator, UserPass}
import spray.routing.directives.AuthMagnet

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class RequestHandler(val password: Option[String]) extends HttpServiceActor {

  private def withAuthentication(innerRoute: Route) =
    if (password.isDefined) {
      authenticate(AuthMagnet.fromContextAuthenticator(
        new BasicHttpAuthenticator(
          "NF has been password protected",
          (userPass: Option[UserPass]) => Future(
            if (userPass.exists(_.pass == password.get)) Some(true)
            else None
          )
        )
      ))(authenticated => innerRoute)
    } else {
      innerRoute
    }

  def receive = runRoute {
    path(REST_API_PATH_PREFIX / EXECUTE_PATH) {
      withAuthentication {
        post {
          entity(as[Stage]) { stage =>
            complete {
              val maybeResultId = StageService.getById(stage.id).map(ExecutionService.createFromStage(_)).get
              // TODO return the URL to the new execution result endpoint
              if (maybeResultId.isSuccess) {
                maybeResultId.get.toString
              } else {
                maybeResultId.failed.get.toString
              }
            }
          }
        }
      }
    } ~ path(REST_API_PATH_PREFIX / EXECUTE_PATH / IntNumber) { executionId =>
      withAuthentication {
        respondWithMediaType(`application/json`) {
          get {
            complete {
              val maybeExecutionResult = ExecutionService.getById(executionId)
              if (maybeExecutionResult.isDefined) {
                val maybeFinishedExecutionResult = maybeExecutionResult.get
                if (maybeFinishedExecutionResult.isDefined) {
                  val maybeSuccessfulExecutionResult = maybeFinishedExecutionResult.get
                  if (maybeSuccessfulExecutionResult.isSuccess) {
                    println(maybeSuccessfulExecutionResult.get)
                    StatusCodes.OK
                  } else {
                    println(maybeSuccessfulExecutionResult.failed.get)
                    StatusCodes.OK
                  }
                } else {
                  println("Execution not finished, yet.")
                  StatusCodes.OK
                }
              } else {
                StatusCodes.NotFound
              }
            }
          }
        }
      }
    } ~ path(REST_API_PATH_PREFIX / STAGES_PATH) {
      withAuthentication {
        respondWithMediaType(`application/json`) {
          get {
            complete {
              JsArray(StageService.getAll.map{ case (id, stage) => JsNumber(id)}.toVector)
            }
          }
        }
      }
    } ~ path(REST_API_PATH_PREFIX / STAGES_PATH / IntNumber) { stageId =>
      withAuthentication {
        get {
          complete {
            val stage = StageService.getById(stageId).get
            stage
          }
        } ~ put {
          entity(as[Stage]) { stage =>
            complete {
              StageService.updateById(stageId, stage)
              StatusCodes.OK
            }
          }
        } ~ delete {
          complete {
            if (StageService.deleteById(stageId))
              StatusCodes.OK
            else
              StatusCodes.NotFound
          }
        }
      }
    } ~ pathPrefix(WEBAPP_PATH_PREFIX) {
      withAuthentication {
        getFromResourceDirectory("webapp")
      }
    }
  }

}

object RequestHandler {

  def props(password: Option[String]): Props = Props(new RequestHandler(password))

  val REST_API_PATH_PREFIX = "rest"
  val STAGES_PATH = "stages"
  val WEBAPP_PATH_PREFIX = "webapp"
  val EXECUTE_PATH = "executions"

}
