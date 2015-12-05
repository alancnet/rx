package net.alanc.rx

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import akka.io.{Tcp, IO}
import akka.util.ByteString
import net.alanc.rx.RxTcp.TcpUnsubscribed
import rx.lang.scala.{Subscription, Subject, Observable}
import rx.lang.scala.subjects.PublishSubject

import scala.collection.mutable
import scala.concurrent.duration.Duration
import list._

object RxTcp {
  case class TcpUnsubscribed()
  def client(outgoing: Observable[ByteString], remote: InetSocketAddress, onConnect: () => Unit = () => {}, transformIncoming: Subject[ByteString] => Observable[ByteString] = x => x): Observable[ByteString] = {

    val incoming = transformIncoming(PublishSubject[ByteString]())
    val (subscribee, states) = RxState(incoming)

    states.debug("~~~~ State $0")
      .scan(RxTcpState(null, null)) {
      case (state, RxState.Subscribed) =>
        println(s"Opening TCP Connection to $remote")
        val actor = ManagedActor("RxTcpClient", Props(classOf[RxTcpClient], remote, incoming, outgoing, onConnect))
        // Convert outgoing to a FlushSubject since the subject won't be subscribed to until the client is connected,
        // and data may be published to it before that.
        state.copy(
          actor = actor
        )
      case (state, RxState.Unsubscribed) =>
        println(s"Closing connection to $remote")
        state.actor ! TcpUnsubscribed()
        state.copy(actor = null)
      case (state, _) => state
    }.subscribe(_ => {})
    subscribee

  }

  def server[S <: RxTcpSession](/*onNewClient: Client => Observable[ByteString],*/ sessionFactory: RxTcp.Client => S, local: InetSocketAddress): Observable[S] = {
    val sessions = Subject[S]()

    val (subscribee, states) = RxState(sessions)
    states.scan(RxTcpState(null, null)) {
      case (state, RxState.Subscribed) =>
        println(s"Listening on $local")
        val actor = ManagedActor("RxTcpServer", Props(classOf[RxTcpListener], sessionFactory, sessions, local))
        state.copy(
          actor = actor
        )
      case (state, RxState.Unsubscribed) =>
        println(s"Stop listening on $local")
        state.actor ! Tcp.Unbind
        state.copy(system = null, actor = null)
      case (state, _) => state
    }.subscribe(_ =>{})

    subscribee
  }

  case class RxTcpState(system: ActorSystem, actor: ActorRef)

  case class Client(incoming: Observable[ByteString], remote: InetSocketAddress)

}

class RxTcpClient(remote: InetSocketAddress, incoming: Subject[ByteString], outgoing: Observable[ByteString], onConnect: () => Unit) extends Actor {

  import context.system
  import Tcp._


  println(s"Connecting to $remote")
  IO(Tcp) ! Connect(remote)

  def receive: Actor.Receive = {
    case Connected(remoteAddress, localAddress) =>
      val connection = sender()
      val session = context.actorOf(Props(classOf[RxTcpAkkaSession], incoming, outgoing, connection, remoteAddress, onConnect))
      connection ! Register(session)
      context watch session
      context become connected(session)
    case CommandFailed(connect: Connect) =>
      incoming.onError(new Exception(s"Could not connect to ${connect.remoteAddress}"))
      context stop self
  }
  def connected(session: ActorRef): Actor.Receive = {
    case Terminated(_) => context stop self
    case msg : TcpUnsubscribed => session ! msg
  }

}

class RxTcpListener(sessionFactory: RxTcp.Client => RxTcpSession, sessions: Subject[RxTcpSession], local: InetSocketAddress) extends Actor {

  import context.system
  import Tcp._

  IO(Tcp) ! Bind(self, local)
  println("Binding on " + local)

  override def receive: Receive = {
    case Bound(local) => {
      println("Bound on " + local)
      context.become(bound)
    }
  }
  def bound: Receive = {
    case Connected(remoteAddress, localAddress) =>
      println(s"Connected to $remoteAddress")
      val connection = sender()

      val incoming = PublishSubject[ByteString]()
      val session = sessionFactory(RxTcp.Client(incoming, remoteAddress))
      val outgoing = session.outputStream

      val handler = context.actorOf(Props(classOf[RxTcpAkkaSession], incoming, outgoing, connection, remoteAddress, () => {}))
      connection ! Register(handler)
//      outgoing.zip(faucet).map(_._1). /*subscribeOn(NewThreadScheduler()).*/ subscribe(
//        bytes => connection ! Write(bytes, WriteAck()),
//        error => connection ! Close,
//        () => connection ! Close
//      )
      sessions.onNext(session)
    case Unbound => context stop self
  }
}

class RxTcpAkkaSession(incoming: Subject[ByteString], val outgoing: Observable[ByteString], connection: ActorRef, remoteAddress: InetSocketAddress, onConnect: () => Unit) extends Actor {

  import Tcp._
  private val faucet = Subject[Unit]()
  private case class WriteAck() extends Event
  private val closeSubject = Subject[TcpUnsubscribed]()

  val sub = outgoing.merge(closeSubject).zip(faucet).map(_._1). /*subscribeOn(NewThreadScheduler()).*/ subscribe(
    data => {
      data match {
        case bytes:ByteString =>
          println(s"${context.system.name} >> ${bytes.utf8String.replace("\n", s"\n${context.system.name} +> ")}")// For debugging //
          connection ! Write(bytes, WriteAck())
        case TcpUnsubscribed() =>
          connection ! Close
          context stop self
      }
    },
    error => connection ! Close,
    () => connection ! Close
  )
  faucet.onNext()

  def receive = {
    case Received(data) => {
      //println(s"${context.system.name} << ${data.utf8String.replace("\n", s"\n${context.system.name} +< ")}")// For debugging //
      incoming.onNext(data)
    }
    case Closed | PeerClosed =>
      sub.unsubscribe()
      incoming.onCompleted()
      context stop self
    case msg: TcpUnsubscribed => closeSubject.onNext(msg)
    case WriteAck() => faucet.onNext()
    case e => println("RxTcpAkkaSession: " + e)
  }

  onConnect()

}

abstract class RxTcpSession(val endpoint:InetSocketAddress) {
  val outputStream: Observable[ByteString]
  val inputStream: Observable[ByteString]
}

abstract class RxTcpStringProtocolSession[Protocol <: StringProtocol[Message], Message <: ProtocolMessage](val protocol: Protocol, endpoint: InetSocketAddress) extends RxTcpSession(endpoint) {
  val subs = mutable.Queue[Subscription]()

  override val outputStream = protocol.outputStream.tumblingBuffer(Duration("10ms")).filter(_.nonEmpty).map(bytes => ByteString(bytes.toArray)).toFlush

  val sendStream = protocol.sendStream
  val receiveStream = protocol.receiveStream

  def send(message: Message) = sendStream.onNext(message)

  def send(messages: Observable[Message]) = messages.pipe(sendStream)

  def send(messages: Iterable[Message]) = messages.pipe(sendStream)


  protected def onConnect(): Unit = {}

}

class RxTcpStringProtocolClient[Protocol <: StringProtocol[Message], Message <: ProtocolMessage](protocol: Protocol, endpoint: InetSocketAddress) 
  extends RxTcpStringProtocolSession[Protocol, Message](protocol, endpoint) {
  val client = RxTcp.client(outputStream, endpoint, onConnect, transform)
  val inputStream = client
  val subCount: AtomicInteger = new AtomicInteger(0)
  val incoming = protocol.receiveStream
    .doOnSubscribe {
    // println(s"+${subCount.incrementAndGet()}") // For Debugging
    subs.enqueue(client.subscribe(x => {}))
  }
    .doOnUnsubscribe {
    // println(s"-${subCount.decrementAndGet()}") // For Debugging
    subs.dequeue().unsubscribe()
  }
  //client.flatMapIterable(bytes => bytes).pipe(protocol.inputStream)
  def transform(incoming: Observable[ByteString]) = {
    // Pipe data to protocol without establishing a subscription.
    incoming
      .flatMapIterable(bytes => bytes)
      .pipe(protocol.inputStream)
    incoming
  }
}

/*class RxTcpStringProtocolServer[Protocol <: StringProtocol[Message], Message <: ProtocolMessage, Session <: RxTcpStringProtocolSession[Protocol, Message]]
(endpoint: InetSocketAddress, sessionFactory: RxTcp.Client => Session) {

//  protected def onNewClient(client: RxTcp.Client): Observable[ByteString] = {
//    val session = sessionFactory(client)
//    session.inputStream.flatMapIterable(bytes => bytes).pipe(session.protocol.inputStream)
//
//    session.outputStream.tumblingBuffer(Duration("10ms")).filter(_.nonEmpty).map(bytes => ByteString(bytes.toArray)).toFlush
//  }

  def mySessionFactory(client:RxTcp.Client): Session = {
    val yourSession = sessionFactory(client)
    yourSession.inputStream.flatMapIterable(bytes => bytes).pipe(yourSession.protocol.inputStream)
    yourSession.outputStream.tumblingBuffer(Duration("10ms")).filter(_.nonEmpty).map(bytes => ByteString(bytes.toArray)).toFlush
    yourSession
  }

  val sessions = RxTcp.server(mySessionFactory, endpoint)

}*/

object RxTcpStringProtocol {
  def server[Protocol <: StringProtocol[Message], Message <: ProtocolMessage, Session <: RxTcpStringProtocolSession[Protocol, Message]]
  (endpoint: InetSocketAddress, sessionFactory: RxTcp.Client => Session):Observable[Session] = {

    //  protected def onNewClient(client: RxTcp.Client): Observable[ByteString] = {
    //    val session = sessionFactory(client)
    //    session.inputStream.flatMapIterable(bytes => bytes).pipe(session.protocol.inputStream)
    //
    //    session.outputStream.tumblingBuffer(Duration("10ms")).filter(_.nonEmpty).map(bytes => ByteString(bytes.toArray)).toFlush
    //  }

    def mySessionFactory(client:RxTcp.Client): Session = {
      val yourSession = sessionFactory(client)
      yourSession.inputStream.flatMapIterable(bytes => bytes).pipe(yourSession.protocol.inputStream)
      // removed // yourSession.outputStream.tumblingBuffer(Duration("10ms")).filter(_.nonEmpty).map(bytes => ByteString(bytes.toArray)).toFlush
      yourSession
    }

    RxTcp.server[Session](mySessionFactory, endpoint)

  }
}
