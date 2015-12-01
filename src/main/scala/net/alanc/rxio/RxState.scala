package net.alanc.rxio

import rx.lang.scala.Observable
import rx.lang.scala.subjects.PublishSubject

object RxState {
  trait StateEvent
  case class Subscribe() extends StateEvent
  case class Unsubscribe() extends StateEvent
  case class Complete() extends StateEvent
  case class Terminate() extends StateEvent

  case class ObservableState(subscribed:Int, completed:Boolean, terminated:Boolean, stateEvent: StateEvent)

  val Subscribed = ObservableState(1, completed = false, terminated = false, Subscribe())
  val Unsubscribed = ObservableState(0, completed = false, terminated = false, Unsubscribe())

  def apply[T](obs:Observable[T]):(Observable[T], Observable[ObservableState]) = {
    val events = PublishSubject[StateEvent]()

    val subscribee = obs
      .doOnSubscribe{events.onNext(Subscribe())}
      .doOnUnsubscribe{events.onNext(Unsubscribe())}
      .doOnCompleted{events.onNext(Complete())}
      .doOnTerminate{events.onNext(Terminate())}

    val states = events.scan(ObservableState(0, completed = false, terminated = false, stateEvent = null)){
      case (state, event) =>
        event match {
          case Subscribe() => state.copy(subscribed = state.subscribed + 1, stateEvent = event)
          case Unsubscribe() => state.copy(subscribed = state.subscribed - 1, stateEvent = event)
          case Complete() => state.copy(completed = true, stateEvent = event)
          case Terminate() => state.copy(terminated = true, stateEvent = event)
        }

    }
    (subscribee, states)
  }
}