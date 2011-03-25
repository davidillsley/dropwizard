package com.yammer.dropwizard

import collection.{immutable, mutable}
import util.JarAware
import com.codahale.logula.Logging
import com.google.inject.Module
import com.yammer.dropwizard.cli.{ServerCommand, Command}
import modules.{GuiceModule, ServerModule, RequestLogHandlerModule}
import com.yammer.metrics.core.HealthCheck
import com.google.inject.multibindings.Multibinder

trait Service extends Logging with JarAware {
  private val modules = new mutable.ArrayBuffer[Module]() ++ Seq(new RequestLogHandlerModule, new ServerModule)
  protected def require(modules: Module*) { this.modules ++= modules }

  private var commands = new immutable.TreeMap[String, Command]
  protected def provide(commands: Command*) { commands.foreach { c => this.commands += c.name -> c } }
  provide(new ServerCommand(this))
  
  protected def healthCheck[A <: HealthCheck](implicit mf: Manifest[A]) {
    modules += new GuiceModule {
      def configure {
        val healthchecks = Multibinder.newSetBinder(binder, classOf[HealthCheck])
        healthchecks.addBinding.to(mf.erasure.asInstanceOf[Class[HealthCheck]])
      }
    }
  }

  def name: String

  def banner: Option[String] = None

  private def printUsage(error: Option[String] = None) {
    for (msg <- error) {
      System.err.printf("%s\n\n", msg)
    }

    printf("%s <command> [arg1 arg2]\n\n", jarSyntax)


    println("Commands")
    println("========\n")
    for (cmd <- commands.values) {
      cmd.printUsage(jarSyntax)
      println("\n")
    }
  }

  def main(args: Array[String]) {
    args.toList match {
      case Nil | "-h" :: Nil | "--help" :: Nil => printUsage()
      case command :: args => {
        commands.get(command) match {
          case Some(cmd) => {
            cmd.execute(jarSyntax, modules.toSeq, args)
          }
          case None => printUsage(Some("Unrecognized command: " + command))
        }
      }
    }
  }
}

