import net.alanc.rx.FlushSubject
import rx.lang.scala.Notification.{OnCompleted, OnNext}
import rx.lang.scala.Subject
import net.alanc.rx.list._
import org.scalatest._

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

class FlushSubjectTest extends FlatSpec with Matchers {
  "FlushSubject" should "emit items to the first observer" in {
    val flushSubject = FlushSubject[String]()
    flushSubject.onNext("prior")
    flushSubject.onNext("prior")
    flushSubject.onCompleted()
    val queue = flushSubject.observable.materialize.toQueue
    queue.toList should be (OnNext("prior") :: OnNext("prior") :: OnCompleted :: Nil)
  }
  "FlushSubject" should "emit items after subscription to the observer" in {
    val flushSubject = FlushSubject[String]()
    flushSubject.onNext("prior")
    flushSubject.onNext("prior")
    val queue = flushSubject.observable.materialize.toQueue
    flushSubject.onNext("after")
    flushSubject.onNext("after")
    queue.toList should be (OnNext("prior") :: OnNext("prior") :: OnNext("after") :: OnNext("after") :: Nil)
  }
  "FlushSubject" should "emit only new items to second subscriber" in {
    val flushSubject = FlushSubject[String]()
    flushSubject.onNext("prior")
    flushSubject.onNext("prior")
    val queue = flushSubject.observable.toQueue
    val queue2 = flushSubject.observable.toQueue
    flushSubject.onNext("after")
    flushSubject.onNext("after")
    queue.toList should be ("prior" :: "prior" :: "after" :: "after" :: Nil)
    queue2.toList should be ("after" :: "after" :: Nil)
  }
  "FlushSubject" should "ignore elements emitted after complete but before observe" in {
    val flushSubject = FlushSubject[String]()
    flushSubject.onNext("prior")
    flushSubject.onNext("prior")
    flushSubject.onCompleted()
    flushSubject.onNext("after")
    flushSubject.onNext("after")
    val queue = flushSubject.observable.materialize.toQueue
    queue.toList should be (OnNext("prior") :: OnNext("prior") :: OnCompleted :: Nil)
  }
  "FlushSubject" should "ignore elements emitted before observe and after complete" in {
    val flushSubject = FlushSubject[String]()
    flushSubject.onNext("prior")
    flushSubject.onNext("prior")
    val queue = flushSubject.observable.materialize.toQueue
    flushSubject.onCompleted()
    flushSubject.onNext("after")
    flushSubject.onNext("after")
    queue.toList should be (OnNext("prior") :: OnNext("prior") :: OnCompleted :: Nil)
  }



  "FlushSubject" should "emit items to the first subscriber" in {
    val flushSubject = FlushSubject[String]()
    flushSubject.onNext("prior")
    flushSubject.onNext("prior")
    flushSubject.onCompleted()
    val queue = flushSubject.observable.toQueue
    queue.toList should be ("prior" :: "prior" :: Nil)
  }
  "FlushSubject" should "emit items after subscription to the subscriber" in {
    val flushSubject = FlushSubject[String]()
    flushSubject.onNext("prior")
    flushSubject.onNext("prior")
    val queue = flushSubject.observable.toQueue
    flushSubject.onNext("after")
    flushSubject.onNext("after")
    queue.toList should be ("prior" :: "prior" :: "after" :: "after" :: Nil)
  }
  "FlushSubject" should "ignore elements emitted after complete but before subscribe" in {
    val flushSubject = FlushSubject[String]()
    flushSubject.onNext("prior")
    flushSubject.onNext("prior")
    flushSubject.onCompleted()
    flushSubject.onNext("after")
    flushSubject.onNext("after")
    val queue = flushSubject.observable.toQueue
    queue.toList should be ("prior" :: "prior" :: Nil)
  }
  "FlushSubject" should "ignore elements emitted before subscribe and after complete" in {
    val flushSubject = FlushSubject[String]()
    flushSubject.onNext("prior")
    flushSubject.onNext("prior")
    val queue = flushSubject.observable.toQueue
    flushSubject.onCompleted()
    flushSubject.onNext("after")
    flushSubject.onNext("after")
    queue.toList should be ("prior" :: "prior" :: Nil)
  }
//  "FlushSubject" should "only allow one subscriber" in {
//    val flushSubject = FlushSubject[String]()
//    flushSubject.onNext("1")
//    val queue = flushSubject.observable.toQueue
//    flushSubject.onNext("2")
//    (Try(flushSubject.observable.toQueue) match {
//      case Success(_) => "Success"
//      case Failure(_) => "Failure"
//    }) should be ("Failure")
//    flushSubject.onNext("3")
//    queue.toList should be ("1" :: "2" :: "3" :: Nil)
//  }
//  "FlushSubject" should "reject a second subscriber" in {
//    val flushSubject = FlushSubject[String]()
//    val subject = Subject[String]()
//    flushSubject.observable.subscribe(_=>{})
//    (Try(flushSubject.observable.subscribe(_=>{})) match {
//      case Success(_) => "Success"
//      case Failure(_) => "Failure"
//    }) should be ("Failure")
//  }
//  "FlushSubject" should "reject a second observer" in {
//    val flushSubject = FlushSubject[String]()
//    val subject = Subject[String]()
//    flushSubject.observable.subscribe(subject)
//    (Try(flushSubject.observable.subscribe(subject)) match {
//      case Success(_) => "Success"
//      case Failure(_) => "Failure"
//    }) should be ("Failure")
//  }
  "Subject subscribed to subject" should "only emit one subscription to the upstream subject" in {
    var count = 0
    val subject = Subject[Int]()
    val upstream = subject.doOnSubscribe{ count = count + 1 }
    val downstream = Subject[Int]()

    count should be (0)
    upstream.subscribe(downstream)
    count should be (1)
    downstream.subscribe(_=>{})
    count should be (1)
    downstream.subscribe(_=>{})
    count should be (1)

  }
}
