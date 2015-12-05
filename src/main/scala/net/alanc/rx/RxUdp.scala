package net.alanc.rx

import java.net.InetSocketAddress

import akka.actor.{Actor, Props, ActorSystem, ActorRef}
import akka.io.{IO, Udp}
import akka.util.ByteString
import net.alanc.rx.RxUdp.Datagram
import rx.lang.scala.schedulers.NewThreadScheduler
import rx.lang.scala.{Observer, Subject, Observable}
import rx.lang.scala.subjects.PublishSubject

object RxUdp {
  case class Datagram(data:ByteString, remote: InetSocketAddress)
  case class RxUdpState(listener:ActorRef)

  def apply(local: InetSocketAddress):Observable[Datagram] = apply(Observable.empty, local)

  def apply(outgoing: Observable[Datagram]):Unit = apply(outgoing, null)

  def apply(outgoing: Observable[Datagram], local: InetSocketAddress):Observable[Datagram] = {
    implicit val sys = ActorSystem("RxUdp")
    val incoming = PublishSubject[Datagram]()
    val (subscribee, states) = RxState(incoming)

    states.scan(RxUdpState(null)) {
      case (state, RxState.Subscribed) =>
        println(s"Opening UDP Listener on $local")
        state.copy(listener = sys.actorOf(Props(classOf[RxUdpListener], local, incoming)))
      case (state, RxState.Unsubscribed) =>
        println(s"Closing UDP Listener on $local")
        state.listener ! Udp.Unbind
        state.copy(listener = null)
      case other =>
        println(s"Unknown state => $other")
        other._1
    }.foreach(x=>println(x))

    val sender:ActorRef = sys.actorOf(
      Props(
        classOf[RxUdpSender],
        outgoing
      )
    )
    subscribee
  }
}
class RxUdpListener(remote:InetSocketAddress, incoming:Subject[RxUdp.Datagram]) extends Actor {
  import context.system
  IO(Udp) ! Udp.Bind(self, remote)
  def receive = {
    case Udp.Bound(localAddress) => context.become(ready(localAddress, sender()))
  }

  def ready(localAddress:InetSocketAddress, socket:ActorRef) : Receive = {
    case Udp.Received(data, sender) =>
      // For debugging // println(s"Received ${data.utf8String} from $sender")
      incoming.onNext(RxUdp.Datagram(data, sender))
    case Udp.Unbind  => socket ! Udp.Unbind
    case Udp.Unbound => context.stop(self)
    case x => println(s"Unknown $x")
  }
}
class RxUdpSender(outgoing:Observable[RxUdp.Datagram]) extends Actor {
  import context.system
  IO(Udp) ! Udp.SimpleSender
  def receive = {
    case Udp.SimpleSenderReady => ready(sender())
  }

  def ready(socket: ActorRef) = {
    outgoing/*.subscribeOn(NewThreadScheduler())*/
      .subscribe(new Observer[Datagram] {
      override def onNext(datagram:Datagram) = {
        // For debugging // println(s"Sending ${datagram.data.utf8String} to ${datagram.remote}")
        socket ! Udp.Send(datagram.data, datagram.remote)
      }
      override def onCompleted() = {
        println("Done")
        context.stop(self)
      }
    })

  }
}