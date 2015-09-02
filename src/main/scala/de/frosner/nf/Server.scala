package de.frosner.nf

import java.awt.Desktop
import java.net.URI

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.apache.log4j.Logger
import spray.http.{StatusCodes}
import spray.http.MediaTypes._
import spray.json.{JsArray, JsNumber}
import spray.routing.SimpleRoutingApp
import spray.routing.authentication._
import spray.routing.Route
import spray.routing.directives.AuthMagnet
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.concurrent.duration._

import StageJsonProtocol.stageFormat
import ExecutionResultJsonProtocol.executionResultFormat
import spray.json.DefaultJsonProtocol._
import spray.httpx.SprayJsonSupport._

import Server._

case class Server(interface: String = Server.DEFAULT_INTERFACE,
                  port: Int = Server.DEFAULT_PORT,
                  password: Option[String] = Option.empty,
                  launchBrowser: Boolean = true)
  extends SimpleRoutingApp {

  private val LOG = Logger.getLogger(this.getClass)

  private implicit val system = ActorSystem("nf-actor-system", {
    val conf = ConfigFactory.parseResources("nf.typesafe-conf")
    conf.resolve()
  })

  private val actorName = "nf-server-actor"

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

  def start() = {
      if (password.isDefined) LOG.info(s"""Basic HTTP authentication enabled (password = ${password.get}). """ +
        s"""Password will be transmitted unencrypted. Do not reuse it somewhere else!""")
      val server = startServer(interface, port, actorName) {
        path(REST_API_PATH_PREFIX / EXECUTE_PATH) {
          withAuthentication {
            respondWithMediaType(`application/json`) {
              post {
                entity(as[Stage]) { stage =>
                  complete {
                    val result = StageService.getById(stage.id).map(ExecutionService.run(_)).get
                    // TODO return URI to execution result resource instead
                    println(result)
                    result
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
                if (StageService.removeById(stageId))
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
      Thread.sleep(1000)
      if (launchBrowser && Desktop.isDesktopSupported()) {
        LOG.info("Opening browser")
        Desktop.getDesktop().browse(new URI( s"""http://$interface:$port/$WEBAPP_PATH_PREFIX/index.html"""))
      }
  }

  def stop() = {
    LOG.info("Stopping server")
    system.scheduler.scheduleOnce(1.milli)(system.shutdown())(system.dispatcher)
  }

}

object Server {

  val DEFAULT_INTERFACE = "localhost"
  val DEFAULT_PORT = 23080

  val REST_API_PATH_PREFIX = "rest"
  val STAGES_PATH = "stages"
  val WEBAPP_PATH_PREFIX = "webapp"
  val EXECUTE_PATH = "executions"

  /**
   * Create a server instance bound to default port and interface, without opening a browser window.
   *
   * @param name of the server
   * @return A server bound to default port and interface.
   */
  def withoutLaunchingBrowser(name: String) = Server(name, launchBrowser = false)

}
