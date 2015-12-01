package net.alanc.rxio

import rx.lang.scala.subjects.ReplaySubject
import rx.lang.scala.{Observer, Subscription, Observable, Subject}

import encoding._
import ListComprehension._

trait ProtocolMessage

abstract class StringProtocol[Message <: ProtocolMessage] {
  val inputStream = Subject[Byte]()
  val outputStream = Subject[Byte]()
  val sendStream = Subject[Message]()
  val receiveStream = Subject[Message]()
  val encoding: CharacterEncoding = ASCII

  def decode(line: String): Message

  def encode(msg: Message): String

  def encode(msgs: Iterable[Message]): String = msgs.map(encode).mkString("")

  def send(msg: Message) = sendStream.onNext(msg)

  def receive(handler: Message => Unit) = receiveStream.foreach(handler)

  var bytesToSend:Iterable[Byte] = null
  var linesToSend:Iterable[String] = null

  protected def sendBytes(bytes:Observable[Byte]): Int = {
    if (bytesToSend != null) throw new Exception("Nested sendBytes call.")
    // Blocking is necessary here because you need to know the length of the bytes before you send the header message.
    val giantByteArray = bytes.toBlocking.toIterable.toArray
    bytesToSend = giantByteArray
    giantByteArray.length
  }

  protected def sendLines(lines:Observable[String]):Int = {
    if (linesToSend != null) throw new Exception("Nested sendLines call.")
    val giantLineArray = lines.toBlocking.toIterable.toArray
    linesToSend = giantLineArray
    giantLineArray.length
  }

  protected def readBytes():Observable[Byte] = {
    val stream = FlushSubject[Byte]()
    byteStream = Some(stream) // Side effect
    val byteSub = inputStream.subscribe(_=>{})

    stream
    .doOnUnsubscribe{
      stream.onCompleted()
      byteStream = None // Side effect
      byteSub.unsubscribe()
    }
  }

  protected def readLines(): Observable[String] = {
    val stream = FlushSubject[Byte]()
    lineStream = Some(stream) // Side effect
    val lineSub = inputStream.subscribe(_=>{})

    encoding.decode(stream)
    .splitBySlice("\r\n")
    .map(chars => new String(chars.toArray))
    .doOnUnsubscribe{
      stream.onCompleted()
      lineStream = None // Side effect
      lineSub.unsubscribe()
    }
  }

  private var byteStream:Option[Observer[Byte]] = None
  private var lineStream:Option[Observer[Byte]] = None

  private val messageStream = Subject[Byte]()

  val router = inputStream.foreach(byte => {
    List(byteStream, lineStream, Some(messageStream)).flatten.take(1).last.onNext(byte)
  })

  // Pipe incoming text to received messages
  encoding.decode(messageStream)
  .splitBySlice("\r\n")
    .map(_.mkString(""))
    .map(decode).pipe(receiveStream)


  // Pipe sent messages to outgoing text
  sendStream.map(encode)
    .foreach(line => {
    encoding.encode(Observable.from(line.toList)).pipe(outputStream)
    encoding.encode("\r\n").pipe(outputStream)
      if (bytesToSend != null) {
        bytesToSend.pipe(outputStream)
        bytesToSend = null
      }
      if (linesToSend != null) {
        linesToSend.foreach(line2 => {
          encoding.encode(Observable.from(line2.toList)).pipe(outputStream)
          encoding.encode("\r\n").pipe(outputStream)
        })
        linesToSend = null
      }
  })

}
