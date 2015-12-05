package net.alanc.rx

import rx.lang.scala.Notification.{OnCompleted, OnError, OnNext}
import rx.lang.scala.Observable
import rx.lang.scala.subjects.ReplaySubject

import scala.concurrent.{Future, TimeoutException, CanAwait, Awaitable}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

import list._

object RxFuture {
  def apply[T](input:Observable[T]) = new RxFuture(input)
  implicit def toObservable[T](rxFuture: RxFuture[T]):Observable[T] = rxFuture.observable

  implicit class RxFutureUnit(val future:RxFuture[Unit]) extends AnyVal {
    //def onSuccess(action: => Unit):Unit = future.onSuccess(_=>action)
  }
  def successful[T](value:T) = Observable.from(value :: Nil).future
  def failed(ex:Throwable) = Observable.error(ex).future
}

class RxFuture[T](input:Observable[T]) extends Awaitable[T] {
  private val replay = ReplaySubject[T]()
  private lazy val future = replay.toBlocking.toFuture

  input.take(1).subscribe(replay)

  def observable = replay

  def onSuccess[U](action:T => U) = {
    replay.foreach(t => action(t))
    this
  }
  def onFailure[U](action:Throwable => U) = {
    replay.doOnError(err => action(err)).foreach(_=>{})
    this
  }
  def onComplete[U](action: Try[T] => U) = {
    replay.materialize.take(1).map {
      case OnNext(value) => Success(value)
      case OnError(error) => Failure(error)
      case OnCompleted => Failure(new Exception("Future aborted or something."))
    }.foreach(x => action(x))
    this
  }

  Future

  @throws[InterruptedException](classOf[InterruptedException])
  @throws[TimeoutException](classOf[TimeoutException])
  override def ready(atMost: Duration)(implicit permit: CanAwait) = {
    future.ready(atMost)
    this
  }

  @throws[Exception](classOf[Exception])
  override def result(atMost: Duration)(implicit permit: CanAwait): T = future.result(atMost)
}
