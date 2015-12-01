package net.alanc.rxio

import rx.exceptions.OnErrorFailedException
import rx.lang.scala._
import rx.lang.scala.subjects.ReplaySubject

object FlushSubject {
  def apply[T]() = new FlushSubject[T]()

  implicit def toObservable[T](flushSubject: FlushSubject[T]): Observable[T] = flushSubject.observable

  var id = 0
}


class FlushSubject[T] extends Observer[T] {

  val id = FlushSubject.id
  FlushSubject.id = FlushSubject.id + 1

  private val queue = ReplaySubject[T]()
  private var output: Option[Subject[T]] = None

  private val subject = Subject[T]
  private var flushed = false

  private val downstream = Subject[T]

  override def onNext(value: T) = synchronized {
    output.getOrElse(queue).onNext(value)
  }

  override def onCompleted = synchronized {
    queue.onCompleted()
    subject.onCompleted()
  }

  override def onError(error: Throwable) = synchronized {
    queue.onError(error)
    subject.onError(error)
  }


  var observable:Observable[T] = queue
    .doOnSubscribe {
      if (flushed) {
        // Cannot subscribe more than once to a FlushSubject, since the second subscriber would get the priors, but not
        // anything emitted since then.
      }
      else {
        flushed = true
        observable = subject
        output = Some(subject)
        queue.onCompleted()

      }
    } ++ subject
}

