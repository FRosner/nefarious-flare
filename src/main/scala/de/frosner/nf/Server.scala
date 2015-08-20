package de.frosner.nf

import java.awt.Desktop
import java.net.URI

import akka.actor.ActorSystem
import com.twitter.util.Eval
import com.typesafe.config.ConfigFactory
import org.apache.log4j.Logger
import org.apache.spark.sql.DataFrame
import spray.http.{StatusCodes, HttpResponse}
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
import scala.collection.mutable

import StageJsonProtocol._
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

  private var stages: mutable.Map[Int, Stage] = mutable.HashMap.empty ++ Map(
    1 -> Stage(1, "Stage 1", "(in: org.apache.spark.sql.DataFrame) => in"),
    2 -> Stage(2, "Stage 2", "(in: org.apache.spark.sql.DataFrame) => \n  in.select(in(\"col1\").as(\"renamed\"))")
  )

  def start() = {
      if (password.isDefined) LOG.info(s"""Basic HTTP authentication enabled (password = ${password.get}). """ +
        s"""Password will be transmitted unencrypted. Do not reuse it somewhere else!""")
      val server = startServer(interface, port, actorName) {
        path("execute") {
          withAuthentication {
            complete {
              val eval = new Eval(None)
              val transformation = eval("import org.apache.spark.sql.DataFrame\n" +
                "(in: DataFrame, out: DataFrame) => out").asInstanceOf[(DataFrame, DataFrame) => DataFrame]
              transformation(null, null).toString
            }
          }
        } ~ path(REST_API_PATH_PREFIX / STAGES_PATH) {
          withAuthentication {
            respondWithMediaType(`application/json`) {
              get {
                complete {
                  JsArray(stages.map{ case (id, stage) => JsNumber(id)}.toVector)
                }
              }
            }
          }
        } ~ path(REST_API_PATH_PREFIX / STAGES_PATH / IntNumber) { stageId =>
          withAuthentication {
            get {
              complete {
                val stage = stages(stageId)
                LOG.info("Serving stage " + stage)
                stage
              }
            } ~ put {
              entity(as[Stage]) { stage =>
                complete {
                  LOG.info("Updating stage " + stage)
                  stages(stage.id) = stage
                  StatusCodes.OK
                }
              }
            } ~ delete {
              complete {
                val stage = stages(stageId)
                LOG.info("Removing stage " + stage)
                stages -= stage.id
                StatusCodes.OK
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

  /**
   * Create a server instance bound to default port and interface, without opening a browser window.
   *
   * @param name of the server
   * @return A server bound to default port and interface.
   */
  def withoutLaunchingBrowser(name: String) = Server(name, launchBrowser = false)

}
