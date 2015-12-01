package net.alanc.rxio

import java.util.concurrent.ThreadFactory

import akka.actor.ActorSystem.Settings
import akka.actor._
import akka.dispatch.{Dispatchers, Mailboxes}
import akka.event.Logging.{Error, LoggerInitialized, InitializeLogger}
import akka.event.{LoggingAdapter, EventStream}
import com.typesafe.config.{ConfigFactory, Config}
import rx.lang.scala.{Subject, Observable}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.Duration


object ManagedActor {
  private var count:Long = 0
  private def getId = synchronized{
    count = count + 1
    count
  }

  private class Monitor() extends Actor {
    override def receive: Actor.Receive = {
      case Terminated(_) => {
        println(s"Shutting down actor system ${context.system.name}")
        context.system.shutdown()
      }
      case actor:ActorRef => {
        println(s"Setting up watch on $actor")
        context watch actor
      }
    }
  }

  def apply(name:String, props: Props) = {
    val system = ActorSystem(s"${name}-$getId",
      ConfigFactory.parseString(
        """
          akka {
            loggers = ["com.athlinks.chronotrack.akka.ConsoleLogger"],
            loglevel = "DEBUG"
          }
        """.stripMargin),
      classOf[ConsoleLogger].getClassLoader
    )
    val child = system.actorOf(props)
    system.actorOf(Props(classOf[Monitor])) ! child

    child
  }
  def stream(name:String, props: Props):Observable[ActorRef] = {
    val input = Subject[ActorRef]()
    val (subscribee, states) = RxState(input)

    var actor:ActorRef = null

    states.foreach{
      case RxState.Subscribed =>
        println(s"Starting up actor: $name")
        actor = apply(name, props)
      case RxState.Unsubscribed =>
        println(s"Shut down actor: ${name}")
        actor ! PoisonPill
        actor = null
    }

    subscribee
      .doOnSubscribe{input.onNext(actor)}
      .take(1)
  }


}

class ConsoleLogger extends Actor {
  override def receive: Actor.Receive = {
    case InitializeLogger(bus) => sender() ! LoggerInitialized
    case e: Error => {
      println (s"${context.system.name} Error: ${e.message};}")
      e.cause.printStackTrace()
    }
    case all => println(s"${context.system.name}: $all")
  }
}
