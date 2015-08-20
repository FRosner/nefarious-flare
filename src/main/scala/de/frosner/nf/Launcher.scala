package de.frosner.nf

object Launcher extends App {

  val server = Server(interface = "0.0.0.0", launchBrowser = false)
  server.start()

  while (true)
    Thread.sleep(1000)

}
