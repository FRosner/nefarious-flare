package de.frosner.nf

import java.awt.Desktop
import java.net.URI

import akka.actor.{ActorRef, ActorSystem}
import akka.io.IO
import com.typesafe.config.ConfigFactory
import org.apache.log4j.Logger
import spray.can.Http

import scala.concurrent.duration._

case class Server(interface: String = Server.DEFAULT_INTERFACE,
                  port: Int = Server.DEFAULT_PORT,
                  password: Option[String] = Option.empty,
                  launchBrowser: Boolean = true) {

  private val LOG = Logger.getLogger(this.getClass)

  private implicit val system = ActorSystem("nf-actor-system", {
    val conf = ConfigFactory.parseResources("nf.typesafe-conf")
    conf.resolve()
  })

  private val actorName = "nf-server-actor"

  def start() = {
    if (password.isDefined) LOG.info( s"""Basic HTTP authentication enabled (password = ${password.get}). """ +
      s"""Password will be transmitted unencrypted. Do not reuse it somewhere else!""")

    val requestHandler: ActorRef = system.actorOf(RequestHandler.props(password), name = actorName)

    IO(Http) ! Http.Bind(requestHandler, interface, port)
    Thread.sleep(1000)
    if (launchBrowser && Desktop.isDesktopSupported()) {
      LOG.info("Opening browser")
      Desktop.getDesktop().browse(new URI( s"""http://$interface:$port/${RequestHandler.WEBAPP_PATH_PREFIX}/index.html"""))
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

  /**
   * Create a server instance bound to default port and interface, without opening a browser window.
   *
   * @param name of the server
   * @return A server bound to default port and interface.
   */
  def withoutLaunchingBrowser(name: String) = Server(name, launchBrowser = false)

}
