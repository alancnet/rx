package net.alanc.rx

import rx.lang.scala.Observable
import rx.lang.scala.subjects.ReplaySubject

object RxPromise {
  def apply[T]() = new RxPromise[T]()
  implicit def toObservable[T](rxPromise: RxPromise[T]):Observable[T] = rxPromise.observable

  implicit class RxPromiseUnit(val promise: RxPromise[Unit]) extends AnyVal {
    def success = promise.success({})
  }
}

class RxPromise[T]() {
  val observable = ReplaySubject[T]()
  def success(value:T) = {
    observable.onNext(value)
    this
  }
  def failure(error:Throwable) = {
    observable.onError(error)
    this
  }
  def future = RxFuture(observable)
}

